/*
 * Copyright (C) 2007-2014, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AUTH;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.apache.commons.lang.Validate.notNull;

/**
 * <p>Http client with ability to handle GoodData authentication.</p>
 *
 * <h3>Usage</h3>
 *
 * <h4>Authentication using login</h4>
 * <pre>
 * // create HTTP client with your settings
 * HttpClient httpClient = ...
 *
 * // create login strategy, which wil obtain SST via login
 * SSTRetrievalStrategy sstStrategy = new LoginSSTRetrievalStrategy(HttpClientBuilder.create().build(),
 *          new HttpHost("server.com", 123),"user@domain.com", "my secret");
 *
 * // wrap your HTTP client into GoodData HTTP client
 * HttpClient client = new GoodDataHttpClient(httpClient, sstStrategy);
 *
 * // use GoodData HTTP client
 * HttpGet getProject = new HttpGet("/gdc/projects");
 * getProject.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
 * HttpResponse getProjectResponse = client.execute(httpHost, getProject);
 * </pre>
 *
 * <h4>Authentication using super-secure token (SST)</h4>
 *
 * <pre>
 * // create HTTP client
 * HttpClient httpClient = ...
 *
 * // create login strategy (you must somehow obtain SST)
 * SSTRetrievalStrategy sstStrategy = new SimpleSSTRetrievalStrategy("my super-secure token");
 *
 * // wrap your HTTP client into GoodData HTTP client
 * HttpClient client = new GoodDataHttpClient(httpClient, sstStrategy);
 *
 * // use GoodData HTTP client
 * HttpGet getProject = new HttpGet("/gdc/projects");
 * getProject.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
 * HttpResponse getProjectResponse = client.execute(httpHost, getProject);
 * </pre>
 *
 * <h4>Verification levels</h4>
 *
 * If you want to use GDC authentication to interact with WebDav besides the GD REST API,
 * cookie-based authentication won't work, since the cookie issued by GD works only with REST API.
 * However, you can switch to HTTP header-based authentication in this case, by passing {@link VerificationLevel#HEADER}
 * to the client constructor as follows:
 *
 * <pre>
 * // wrap your HTTP client into GoodData HTTP client, enable header-based authentication
 * HttpClient client = new GoodDataHttpClient(httpClient, sstStrategy, VerificationLevel.HEADER);
 * </pre>
 */
public class GoodDataHttpClient implements HttpClient {

    private static final String TOKEN_URL = "/gdc/account/token";
    public static final String COOKIE_GDC_AUTH_TT = "cookie=GDCAuthTT";
    public static final String COOKIE_GDC_AUTH_SST = "cookie=GDCAuthSST";
    public static final String SST_HEADER = "X-GDC-AuthSST";
    public static final String TT_HEADER = "X-GDC-AuthTT";
    public static final String TT_ENTITY = "userToken";
    public static final String LOCK_RW = "gooddata.lock.rw";
    public static final String LOCK_AUTH = "gooddata.lock.auth";

    private enum GoodDataChallengeType {
        SST, TT, UNKNOWN
    }

    private final Log log = LogFactory.getLog(getClass());

    private final HttpClient httpClient;

    private final SSTRetrievalStrategy sstStrategy;

    private final HttpContext context;

    private final VerificationLevel verificationLevel;

    private String sst, tt;

    public GoodDataHttpClient(final HttpClient httpClient, final SSTRetrievalStrategy sstStrategy) {
        this(httpClient, sstStrategy, VerificationLevel.COOKIE);
    }

    /**
     * Construct object.
     * @param httpClient Http client
     * @param sstStrategy super-secure token (SST) obtaining strategy
     */
    public GoodDataHttpClient(final HttpClient httpClient, final SSTRetrievalStrategy sstStrategy, final VerificationLevel verificationLevel) {
        notNull(httpClient);
        notNull(sstStrategy);
        notNull(verificationLevel);
        this.httpClient = httpClient;
        this.sstStrategy = sstStrategy;
        this.verificationLevel = verificationLevel;
        context = new BasicHttpContext();
        final CookieStore cookieStore = new BasicCookieStore();
        context.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

        //this lock is used to ensure that no threads will try to send requests while authentication is performed
        context.setAttribute(LOCK_RW, new ReentrantReadWriteLock());

        //this lock guards that only one thread enters the authentication (obtaining TT/SST) section
        context.setAttribute(LOCK_AUTH, new ReentrantLock());
    }

    /**
     * Construct object.
     * @param sstStrategy super-secure token (SST) obtaining strategy
     */
    public GoodDataHttpClient(final SSTRetrievalStrategy sstStrategy) {
        this(HttpClientBuilder.create().build(), sstStrategy);
    }

    /**
     * Construct object.
     * @param sstStrategy super-secure token (SST) obtaining strategy
     */
    public GoodDataHttpClient(final SSTRetrievalStrategy sstStrategy, final VerificationLevel verificationLevel) {
        this(HttpClientBuilder.create().build(), sstStrategy, verificationLevel);
    }

    private GoodDataChallengeType identifyGoodDataChallenge(final HttpResponse response) {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
            final Header[] headers = response.getHeaders(AUTH.WWW_AUTH);
            if (headers != null) {
                for (final Header header : headers) {
                    final String challenge = header.getValue();
                    if (challenge.contains(COOKIE_GDC_AUTH_SST)) {
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
        EntityUtils.consume(originalResponse.getEntity());

        final Lock authLock = (Lock) context.getAttribute(LOCK_AUTH);
        final boolean entered = authLock == null || authLock.tryLock();

        if (entered) {
            try {
                //only one thread requiring authentication will get here.
                final ReadWriteLock rwLock = (ReadWriteLock) context.getAttribute(LOCK_RW);
                Lock writeLock = null;
                if (rwLock != null) {
                    writeLock = rwLock.writeLock();
                    writeLock.lock();
                }
                boolean doSST = true;
                try {
                    if (challenge == GoodDataChallengeType.TT) {
                        if (this.refreshTt(httpHost)) {
                            doSST = false;
                        }
                    }
                    if (doSST) {
                        sst = (sstStrategy instanceof SSTRetrievalStrategyWithVl) ?
                                ((SSTRetrievalStrategyWithVl) sstStrategy).obtainSst(verificationLevel) :
                                sstStrategy.obtainSst();

                        if (verificationLevel.getLevel() < 2) {
                            CookieUtils.replaceSst(sst, context, httpHost.getHostName());
                        }

                        if (!refreshTt(httpHost)) {
                            throw new GoodDataAuthException("Unable to obtain TT after successfully obtained SST");
                        }
                    }
                } finally {
                    if (writeLock != null) {
                        writeLock.unlock();
                    }
                }
            } finally {
                if (authLock != null) {
                    authLock.unlock();
                }
            }
        }
        return this.execute(httpHost, request, context);
    }

    /**
     * Refresh temporary token.
     * @param httpHost HTTP host
     * @return
     * <ul>
     *     <li><code>true</code> TT refresh successful</li>
     *     <li><code>false</code> TT refresh unsuccessful (SST expired)</li>
     * </ul>
     * @throws GoodDataAuthException error
     */
    private boolean refreshTt(final HttpHost httpHost) {
        log.debug("Obtaining TT");
        final HttpGet getTT = new HttpGet(TOKEN_URL);
        if (verificationLevel.getLevel() > 0) {
            // need to include the token in the header
            getTT.addHeader(SST_HEADER, sst);
        }
        try {
            final HttpResponse response = httpClient.execute(httpHost, getTT, context);
            final int status = response.getStatusLine().getStatusCode();
            switch (status) {
                case HttpStatus.SC_OK:
                    if (verificationLevel.getLevel() > 0) {
                        tt = CookieUtils.extractTokenFromBody(response, TT_ENTITY);
                    }
                    return true;
                case HttpStatus.SC_UNAUTHORIZED:
                    return false;
                default:
                    throw new GoodDataAuthException("Unable to obtain TT, HTTP status: " + status);
            }
        } catch (IOException e) {
            throw new GoodDataAuthException("Error during temporary token refresh: " + e.getMessage(), e);
        } finally {
            getTT.releaseConnection();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public HttpParams getParams() {
        return httpClient.getParams();
    }

    @SuppressWarnings("deprecation")
    @Override
    public ClientConnectionManager getConnectionManager() {
        return httpClient.getConnectionManager();
    }

    @Override
    public HttpResponse execute(HttpHost target, HttpRequest request) throws IOException {
        return execute(target, request, context);
    }

    @Override
    public <T> T execute(HttpHost target, HttpRequest request, ResponseHandler<? extends T> responseHandler) throws IOException {
        return execute(target, request, responseHandler, context);
    }

    @Override
    public <T> T execute(HttpHost target, HttpRequest request, ResponseHandler<? extends T> responseHandler, HttpContext context) throws IOException {
        HttpResponse resp = execute(target, request, context);
        return responseHandler.handleResponse(resp);
    }

    @Override
    public HttpResponse execute(HttpUriRequest request) throws IOException {
        return execute(request, (HttpContext) null);
    }

    @Override
    public HttpResponse execute(HttpUriRequest request, HttpContext context) throws IOException {
        final URI uri = request.getURI();
        final HttpHost httpHost = new HttpHost(uri.getHost(), uri.getPort(),
                uri.getScheme());
        return execute(httpHost, request, context);
    }

    @Override
    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler) throws IOException {
        return execute(request, responseHandler, context);
    }

    @Override
    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler, HttpContext context)
            throws IOException {
        final HttpResponse resp = execute(request, context);
        return responseHandler.handleResponse(resp);
    }

    @Override
    public HttpResponse execute(HttpHost target, HttpRequest request, HttpContext context) throws IOException {
        if (context == null) {
            context = this.context;
        }
        final ReadWriteLock rwLock = (ReadWriteLock) context.getAttribute(LOCK_RW);
        Lock readLock = null;
        if (rwLock != null) {
            readLock = rwLock.readLock();
            readLock.lock();
        }

        if (tt != null) {
            // need to send TT in the header
            request.addHeader(TT_HEADER, tt);
        }

        final HttpResponse resp;
        try {
            resp = this.httpClient.execute(target, request, context);
        } finally {
            if (readLock != null) {
                readLock.unlock();
            }
        }
        return handleResponse(target, request, resp, context);
    }
}
