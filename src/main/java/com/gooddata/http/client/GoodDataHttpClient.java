/*
 * (C) 2025 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.gooddata.http.client.LoginSSTRetrievalStrategy.LOGIN_URL;
import static java.util.Objects.requireNonNull;

/**
 * <p>Http client with ability to handle GoodData authentication.</p>
 *
 * <h3>Usage</h3>
 *
 * <h4>Authentication using login</h4>
 * <pre>
 * import org.apache.hc.client5.http.classic.HttpClient;
 * import org.apache.hc.client5.http.classic.methods.HttpGet;
 * import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
 * import org.apache.hc.core5.http.ClassicHttpResponse;
 * import org.apache.hc.core5.http.ContentType;
 * import org.apache.hc.core5.http.HttpHost;
 *
 * // create HTTP client with your settings
 * HttpClient httpClient = HttpClientBuilder.create().build();
 *
 * // create login strategy, which will obtain SST via login
 * SSTRetrievalStrategy sstStrategy = new LoginSSTRetrievalStrategy("user@domain.com", "my secret");
 *
 * // create host (note: scheme, hostname, port order in HttpClient 5)
 * HttpHost httpHost = new HttpHost("https", "server.com", 443);
 *
 * // wrap your HTTP client into GoodData HTTP client
 * HttpClient client = new GoodDataHttpClient(httpClient, httpHost, sstStrategy);
 *
 * // use GoodData HTTP client
 * HttpGet getProject = new HttpGet("/gdc/projects");
 * getProject.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
 * ClassicHttpResponse getProjectResponse = client.execute(httpHost, getProject);
 * </pre>
 *
 * <h4>Authentication using super-secure token (SST)</h4>
 *
 * <pre>
 * import org.apache.hc.client5.http.classic.HttpClient;
 * import org.apache.hc.client5.http.classic.methods.HttpGet;
 * import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
 * import org.apache.hc.core5.http.ClassicHttpResponse;
 * import org.apache.hc.core5.http.ContentType;
 * import org.apache.hc.core5.http.HttpHost;
 *
 * // create HTTP client
 * HttpClient httpClient = HttpClientBuilder.create().build();
 *
 * // create host (note: scheme, hostname, port order in HttpClient 5)
 * HttpHost httpHost = new HttpHost("https", "server.com", 443);
 *
 * // create login strategy (you must somehow obtain SST)
 * SSTRetrievalStrategy sstStrategy = new SimpleSSTRetrievalStrategy("my super-secure token");
 *
 * // wrap your HTTP client into GoodData HTTP client
 * HttpClient client = new GoodDataHttpClient(httpClient, httpHost, sstStrategy);
 *
 * // use GoodData HTTP client
 * HttpGet getProject = new HttpGet("/gdc/projects");
 * getProject.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
 * ClassicHttpResponse getProjectResponse = client.execute(httpHost, getProject);
 * </pre>
 */
public class GoodDataHttpClient implements HttpClient {

    public static final String COOKIE_GDC_AUTH_TT = "cookie=GDCAuthTT";
    public static final String COOKIE_GDC_AUTH_SST = "cookie=GDCAuthSST";
    static final String TT_HEADER = "X-GDC-AuthTT";
    static final String SST_HEADER = "X-GDC-AuthSST";
    private static final String TOKEN_URL = "/gdc/account/token";
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final HttpClient httpClient;
    private final SSTRetrievalStrategy sstStrategy;
    /**
     * Host performing authentication - eg. issuing TT tokens
     */
    private final HttpHost authHost;
    /**
     * this lock is used to ensure that no threads will try to send requests while authentication is performed
     */
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    /**
     * This lock guards that only one thread enters the authentication (obtaining TT/SST) section.
     * We need second lock we cannot call tryLock() on ReadWriteLock.writeLock as it returns false not only when another thread
     * holds the write lock already (what we want here) but also when another thread holds a read lock (what we do NOT want)
     */
    private final Lock authLock = new ReentrantLock();
    /**
     * current SST (or null if not yet obtained)
     */
    private String sst;
    /**
     * TT to be set into the header (or null if not yet obtained)
     */
    private String tt;

    /**
     * Construct object.
     *
     * @param httpClient  Http client
     * @param sstStrategy super-secure token (SST) obtaining strategy
     * @throws IllegalArgumentException if {@code sstStrategy} argument is not an instance of {@link LoginSSTRetrievalStrategy}
     * @deprecated use {@link #GoodDataHttpClient(HttpClient, HttpHost, SSTRetrievalStrategy)}
     */
    @Deprecated
    public GoodDataHttpClient(final HttpClient httpClient, final SSTRetrievalStrategy sstStrategy) {
        requireNonNull(httpClient);
        this.httpClient = httpClient;
        if (sstStrategy instanceof LoginSSTRetrievalStrategy) {
            this.sstStrategy = sstStrategy;
            this.authHost = ((LoginSSTRetrievalStrategy) sstStrategy).getHttpHost();
            requireNonNull(authHost, "HTTP host cannot be null");
        } else {
            throw new IllegalArgumentException("This constructor is deprecated and works with LoginSSTRetrievalStrategy argument only!");
        }
    }


    /**
     * Construct object.
     *
     * @param sstStrategy super-secure token (SST) obtaining strategy
     * @deprecated use {@link #GoodDataHttpClient(HttpHost, SSTRetrievalStrategy)}
     */
    @Deprecated
    public GoodDataHttpClient(final SSTRetrievalStrategy sstStrategy) {
        this(HttpClientBuilder.create().build(), sstStrategy);
    }

    /**
     * Construct object.
     *
     * @param httpClient  Http client
     * @param authHost    http host
     * @param sstStrategy super-secure token (SST) obtaining strategy
     */
    public GoodDataHttpClient(final HttpClient httpClient, final HttpHost authHost, final SSTRetrievalStrategy sstStrategy) {
        requireNonNull(httpClient);
        requireNonNull(authHost, "HTTP host cannot be null");
        requireNonNull(sstStrategy);
        this.httpClient = httpClient;
        this.authHost = authHost;
        this.sstStrategy = sstStrategy;
    }

    /**
     * Construct object.
     *
     * @param authHost    http host
     * @param sstStrategy super-secure token (SST) obtaining strategy
     */
    public GoodDataHttpClient(final HttpHost authHost, final SSTRetrievalStrategy sstStrategy) {
        this(HttpClientBuilder.create().build(), authHost, sstStrategy);
    }

    private GoodDataChallengeType identifyGoodDataChallenge(final HttpResponse response) {
        if (response.getCode() == HttpStatus.SC_UNAUTHORIZED) {
            final Header[] headers = response.getHeaders(HttpHeaders.WWW_AUTHENTICATE);
            if (headers != null) {
                for (final Header header : headers) {
                    final String challenge = header.getValue();
                    if (challenge.contains(COOKIE_GDC_AUTH_SST)) {
                        // this is actually not used as in refreshTT() we rely on status code only
                        return GoodDataChallengeType.SST;
                    } else if (challenge.contains(COOKIE_GDC_AUTH_TT)) {
                        return GoodDataChallengeType.TT;
                    }
                }
            }
        }
        return GoodDataChallengeType.UNKNOWN;
    }

    private HttpResponse handleResponse(final HttpHost httpHost, final HttpRequest request, final HttpResponse originalResponse, final HttpContext context) throws IOException {
        final GoodDataChallengeType challenge = identifyGoodDataChallenge(originalResponse);
        if (challenge == GoodDataChallengeType.UNKNOWN) {
            return originalResponse;
        }
        EntityUtils.consume(((ClassicHttpResponse) originalResponse).getEntity());

        try {
            if (authLock.tryLock()) {
                //only one thread requiring authentication will get here.
                final Lock writeLock = rwLock.writeLock();
                writeLock.lock();
                boolean doSST = true;
                try {
                    if (challenge == GoodDataChallengeType.TT && sst != null) {
                        if (refreshTt()) {
                            doSST = false;
                        }
                    }
                    if (doSST) {
                        sst = sstStrategy.obtainSst(httpClient, authHost);
                        if (!refreshTt()) {
                            throw new GoodDataAuthException("Unable to obtain TT after successfully obtained SST");
                        }
                    }
                } catch (GoodDataAuthException e) {
                    int code = HttpStatus.SC_UNAUTHORIZED;
                    String message = e.getMessage();
                    return new BasicClassicHttpResponse(code, message);
                } finally {
                    writeLock.unlock();
                }
            } else {
                // the other thread is performing auth and thus is holding the write lock
                // lets wait until it is finished (the write lock is granted) and then continue
                authLock.lock();
            }
        } finally {
            authLock.unlock();
        }
        return this.execute(httpHost, (ClassicHttpRequest) request, context);
    }

    /**
     * Refresh temporary token.
     *
     * @return <ul>
     * <li><code>true</code> TT refresh successful</li>
     * <li><code>false</code> TT refresh unsuccessful (SST expired)</li>
     * </ul>
     * @throws GoodDataAuthException error
     */
    private boolean refreshTt() throws IOException {
        log.debug("Obtaining TT");

        final HttpGet request = new HttpGet(TOKEN_URL);
        HttpResponse response = null;
        try {
            request.setHeader(SST_HEADER, sst);
            response = httpClient.execute(authHost, request, (HttpContext) null);
            final int status = response.getCode();
            return switch (status) {
                case HttpStatus.SC_OK -> {
                    tt = TokenUtils.extractTT(response);
                    yield true;
                }
                case HttpStatus.SC_UNAUTHORIZED ->
                    // we probably may check if SST challenge is present to be sure the problem is the expired SST
                        false;
                default -> throw new GoodDataAuthException("Unable to obtain TT, HTTP status: " + status);
            };
        } finally {
            if (response instanceof ClassicHttpResponse) {
                EntityUtils.consumeQuietly(((ClassicHttpResponse) response).getEntity());
            }
            request.reset();
        }
    }

    private boolean isLogoutRequest(HttpHost target, HttpRequest request) {
        return authHost.equals(target)
                && "DELETE".equals(request.getMethod())
                && request.getRequestUri().startsWith(LOGIN_URL);
    }

    @Override
    public ClassicHttpResponse execute(ClassicHttpRequest request) throws IOException {
        return execute(request, (HttpContext) null);
    }

    @Override
    public ClassicHttpResponse execute(HttpHost target, ClassicHttpRequest request) throws IOException {
        return execute(target, request, (HttpContext) null);
    }

    @Override
    public <T> T execute(ClassicHttpRequest request, HttpContext context,
                         HttpClientResponseHandler<? extends T> responseHandler) throws IOException {
        final ClassicHttpResponse resp = execute(request, context);
        try {
            return responseHandler.handleResponse(resp);
        } catch (HttpException e) {
            throw new IOException("Failed to handle HTTP response: " + e.getMessage(), e);
        }
    }

    @Override
    public ClassicHttpResponse execute(ClassicHttpRequest request, HttpContext context) throws IOException {
        final URI uri;
        try {
            uri = request.getUri();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI in request: " + e.getMessage(), e);
        }
        final HttpHost httpHost = new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
        return execute(httpHost, request, context);
    }

    @Override
    public ClassicHttpResponse execute(HttpHost target, ClassicHttpRequest request, HttpContext context) throws IOException {
        requireNonNull(request, "Request can't be null");
        final boolean logoutRequest = isLogoutRequest(target, request);
        final Lock lock = logoutRequest ? rwLock.writeLock() : rwLock.readLock();

        lock.lock();

        final ClassicHttpResponse resp;
        try {
            if (tt != null) {
                // this adds TT header to EVERY request to ALL hosts made by this HTTP client
                // however the server performs additional checks to ensure client is not using forged TT
                request.setHeader(TT_HEADER, tt);

                if (logoutRequest) {
                    try {
                        sstStrategy.logout(httpClient, target, request.getRequestUri(), sst, tt);
                        tt = null;
                        sst = null;
                        return new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "Logout successful");
                    } catch (GoodDataLogoutException e) {
                        return new BasicClassicHttpResponse(e.getStatusCode(), e.getStatusText());
                    }
                }
            }
            resp = (ClassicHttpResponse) this.httpClient.execute(target, request, context);
        } finally {
            lock.unlock();
        }
        return (ClassicHttpResponse) handleResponse(target, request, resp, context);
    }

    @Override
    public <T> T execute(ClassicHttpRequest request, HttpClientResponseHandler<? extends T> responseHandler)
            throws IOException {
        return execute(request, null, responseHandler);
    }

    @Override
    public <T> T execute(HttpHost target, ClassicHttpRequest request,
                         HttpClientResponseHandler<? extends T> responseHandler) throws IOException {
        return execute(target, request, null, responseHandler);
    }

    @Override
    public <T> T execute(HttpHost target, ClassicHttpRequest request, HttpContext context,
                         HttpClientResponseHandler<? extends T> responseHandler) throws IOException {
        ClassicHttpResponse resp = execute(target, request, context);
        try {
            return responseHandler.handleResponse(resp);
        } catch (HttpException e) {
            throw new IOException("Failed to handle HTTP response: " + e.getMessage(), e);
        }
    }

    private enum GoodDataChallengeType {
        SST, TT, UNKNOWN
    }
}
