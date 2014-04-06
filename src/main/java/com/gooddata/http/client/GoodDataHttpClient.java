/*
 * Copyright (C) 2007-2014, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AUTH;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * // create the client using username and password credentials and wrap your client
 * HttpClient client = GoodDataHttpClient.withUsernamePassword("user@domain.com", "my secret").httpClient(httpClient).build();
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
 * // wrap your HTTP client into GoodData HTTP client
 * HttpClient client = GoodDataHttpClient.withSst(sst).httpClient(httpClient).build();
 *
 * // use GoodData HTTP client
 * HttpGet getProject = new HttpGet("/gdc/projects");
 * getProject.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
 * HttpResponse getProjectResponse = client.execute(httpHost, getProject);
 * </pre>
 *
 * <h4>Token Hostname</h4>
 *
 * When using this client with WebDav, you still need to authenticate against secure.gooddata.com instead of
 * secure-di.gooddata.com. For this case, you can specify the hostname that should be used for obtaining tokens explicitly:
 *
 * <pre>
 * // wrap your HTTP client into GoodData HTTP client, set hostname for retrieving tokens explicitly
 * HttpClient client = GoodDataHttpClient.withUsernamePassword("user@domain.com", "my secret").
 *                          tokenHost(new HttpHost("secure.gooddata.com", -1, "https")).build();
 * </pre>
 *
 * <h4>Verification levels</h4>
 *
 * By default, HTTP headers are used to transmit tokens. If you want to use cookie-based authentication, you can
 * set verification level explicitly as follows:
 *
 * <pre>
 * // wrap your HTTP client into GoodData HTTP client, enable cookie-based authentication
 * HttpClient client = GoodDataHttpClient.withUsernamePassword("user@domain.com", "my secret").
 *                          verification(VerificationLevel.COOKIE).build();
 * </pre>
 */
@SuppressWarnings("deprecation")
public class GoodDataHttpClient implements HttpClient {

    protected static final String TOKEN_URL = "/gdc/account/token";
    protected static final String LOGIN_URL = "/gdc/account/login";
    protected static final String TT_ENTITY = "userToken";
    protected static final String SST_ENTITY = "userLogin";
    public static final String COOKIE_GDC_AUTH_TT = "cookie=GDCAuthTT";
    public static final String COOKIE_GDC_AUTH_SST = "cookie=GDCAuthSST";
    public static final String SST_HEADER = "X-GDC-AuthSST";
    public static final String TT_HEADER = "X-GDC-AuthTT";
    public static final String LOCK_RW = "gooddata.lock.rw";
    public static final String LOCK_AUTH = "gooddata.lock.auth";

    protected enum GoodDataChallengeType {
        SST, TT
    }

    private final Log log = LogFactory.getLog(getClass());

    /** Wrapped http client. */
    protected final HttpClient httpClient;

    private final SSTRetrievalStrategy sstStrategy;

    /** Username for renewing SST (or null if not specified). */
    protected final String username;

    /** Password for renewing SST (or null if not specified). */
    protected final String password;

    /** Http context used for making requests. */
    protected final HttpContext context;

    /** Verification level. */
    protected final VerificationLevel verificationLevel;

    /** Host that should be used for obtaining tokens (or null if the request host should be used). */
    protected final HttpHost tokenHost;

    /** Current SST (or null if not yet obtained). */
    protected String sst;

    /** TT to be set into the header (or null if cookie-based auth is used, or TT not yet obtained). */
    protected String tt;

    /**
     * Constructs the client using a given retrieval strategy and {@link VerificationLevel#COOKIE} verification level.
     * @deprecated You should use static methods of this class to create new client instances.
     * @param httpClient Http client
     * @param sstStrategy super-secure token (SST) obtaining strategy
     */
    public GoodDataHttpClient(final HttpClient httpClient, final SSTRetrievalStrategy sstStrategy) {
        this(httpClient, sstStrategy, null, null, null, VerificationLevel.COOKIE, null);
        notNull(httpClient);
        notNull(sstStrategy);
    }

    /**
     * Constructs the client wrapping the default HttpClient using a given retrieval strategy and {@link VerificationLevel#COOKIE} verification level.
     * @deprecated You should use static methods of this class to create new client instances.
     * @param sstStrategy super-secure token (SST) obtaining strategy
     */
    public GoodDataHttpClient(final SSTRetrievalStrategy sstStrategy) {
        this(HttpClientBuilder.create().build(), sstStrategy);
    }

    /**
     * Returns a new builder for creating {@link GoodDataHttpClient} using username/password credentials.
     * @param username username to be used for renewing SST
     * @param password password to be used for renewing SST
     * @return builder
     */
    public static Builder<GoodDataHttpClient> withUsernamePassword(final String username, final String password) {
        return new GoodDataClientBuilder(username, password);
    }

    /**
     * Returns a new builder for creating {@link GoodDataHttpClient} using existing SST.
     * @param sst valid SST obtained by other means
     * @return builder
     */
    public static Builder<GoodDataHttpClient> withSst(final String sst) {
        return new GoodDataClientBuilder(sst);
    }

    /**
     * Construct object.
     * @param httpClient Http client
     * @param verificationLevel verification level
     */
    protected GoodDataHttpClient(final HttpClient httpClient, final String username, final String password, final String sst,
                                 final VerificationLevel verificationLevel, final HttpHost tokenHost) {
        this(httpClient, null, username, password, sst, verificationLevel, tokenHost);
    }

    private GoodDataHttpClient(final HttpClient httpClient, final SSTRetrievalStrategy sstStrategy,
                               final String username, final String password, final String sst,
                               final VerificationLevel verificationLevel, final HttpHost tokenHost) {
        notNull(verificationLevel);
        this.httpClient = httpClient == null ? HttpClientBuilder.create().build() : httpClient;
        this.sstStrategy = sstStrategy;
        this.username = username;
        this.password = password;
        this.sst = sst;
        this.verificationLevel = verificationLevel;
        this.tokenHost = tokenHost;
        this.context = new BasicHttpContext();

        if (verificationLevel.getLevel() < 2) {
            // we need cookies -> initialize cookie store
            final CookieStore cookieStore = new BasicCookieStore();
            context.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
        }

        //this lock is used to ensure that no threads will try to send requests while authentication is performed
        context.setAttribute(LOCK_RW, new ReentrantReadWriteLock());

        //this lock guards that only one thread enters the authentication (obtaining TT/SST) section
        context.setAttribute(LOCK_AUTH, new ReentrantLock());
    }

    /**
     * Checks if a challenge response was received from the server.
     * @param response response received from the server
     * @return
     * <ul>
     *     <li>{@link GoodDataChallengeType#SST} if SST challenge was received</li>
     *     <li>{@link GoodDataChallengeType#TT} if TT challenge was received</li>
     *     <li>{@code null} otherwise (no challenge)</li>
     * </ul>
     */
    protected GoodDataChallengeType identifyGoodDataChallenge(final HttpResponse response) {
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
        return null;
    }

    /**
     * Handles response from the server. Checks if a challenge was received. If not, returns the response as is.
     * If a challenge was received, performs authentication (TT and/or SST token renewal).
     *
     * @param httpHost host that returned the response
     * @param request original request
     * @param originalResponse original response from the host
     * @param context context
     * @return new response
     * @throws IOException if something went wrong
     */
    protected HttpResponse handleResponse(final HttpHost httpHost, final HttpRequest request, final HttpResponse originalResponse, final HttpContext context) throws IOException {
        final GoodDataChallengeType challenge = identifyGoodDataChallenge(originalResponse);
        if (challenge == null) {
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

                HttpHost realTokenHost = tokenHost == null ? httpHost : tokenHost;

                try {
                    if (challenge == GoodDataChallengeType.TT) {
                        if (sst != null) {
                            // ensure the sst cookie is set
                            CookieUtils.replaceSst(sst, context, realTokenHost.getHostName());
                        }
                        if (this.refreshTt(realTokenHost)) {
                            doSST = false;
                        }
                    }
                    if (doSST) {
                        if (sstStrategy != null) {
                            // TODO: obsolete branch that should be removed after we get rid of sst strategies
                            sst = sstStrategy.obtainSst();
                        } else {
                            refreshSst(realTokenHost);
                        }

                        if (verificationLevel.getLevel() < 2) {
                            CookieUtils.replaceSst(sst, context, realTokenHost.getHostName());
                        }

                        if (!refreshTt(realTokenHost)) {
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
     * @param realTokenHost host to be used to retrieve a new TT token
     * @return
     * <ul>
     *     <li><code>true</code> TT refresh successful</li>
     *     <li><code>false</code> TT refresh unsuccessful (SST expired)</li>
     * </ul>
     * @throws GoodDataAuthException error
     */
    protected boolean refreshTt(final HttpHost realTokenHost) {
        log.debug("Obtaining TT");
        final HttpGet getTT = new HttpGet(TOKEN_URL);
        if (verificationLevel.getLevel() > 0) {
            // need to include the token in the header
            getTT.addHeader(SST_HEADER, sst);
        }
        try {
            final HttpResponse response = httpClient.execute(realTokenHost, getTT, context);
            final int status = response.getStatusLine().getStatusCode();
            switch (status) {
                case HttpStatus.SC_OK:
                    if (verificationLevel.getLevel() > 0) {
                        tt = extractTokenFromBody(response, TT_ENTITY);
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

    /**
     * Refresh SST.
     */
    protected void refreshSst(final HttpHost realTokenHost) {
        if (username != null) {
            sst = obtainSst(log, httpClient, realTokenHost, username, password, verificationLevel);
        }
    }

    static String obtainSst(final Log log, final HttpClient httpClient, final HttpHost httpHost, final String username, final String password, final VerificationLevel verificationLevel) {
        log.debug("Obtaining STT");
        final HttpPost postLogin = new HttpPost(LOGIN_URL);
        try {
            final HttpEntity requestEntity = new StringEntity(createLoginJson(username, password, verificationLevel.getLevel()), ContentType.APPLICATION_JSON);
            postLogin.setEntity(requestEntity);
            postLogin.setHeader("Accept", ContentType.APPLICATION_JSON.toString());
            final HttpResponse response = httpClient.execute(httpHost, postLogin);
            int status = response.getStatusLine().getStatusCode();
            if (status != HttpStatus.SC_OK) {
                throw new GoodDataAuthException("Unable to login: " + status);
            }
            final String sst = extractSST(response, httpHost, verificationLevel.getLevel());
            if (sst == null) {
                throw new GoodDataAuthException("Unable to login. Missing SST Set-Cookie header.");
            }
            return sst;
        } catch (IOException e) {
            throw new GoodDataAuthException("Unable to login: " + e.getMessage(), e);
        } finally {
            postLogin.releaseConnection();
        }
    }

    private static String createLoginJson(final String login, final String password, final int verificationLevel) {
        return "{\"postUserLogin\":{\"login\":\"" + StringEscapeUtils.escapeJavaScript(login) +
                "\",\"password\":\"" + StringEscapeUtils.escapeJavaScript(password) + "\",\"remember\":0" +
                (verificationLevel > 0 ? ",\"verify_level\":" + verificationLevel : "") + "}}";
    }

    private static String extractSST(final HttpResponse response, final HttpHost httpHost, final int verificationLevel) throws IOException {
        String sst;

        if (verificationLevel > 0) {
            // SST sent in the response body
            sst = extractTokenFromBody(response, SST_ENTITY);
        } else {
            sst = CookieUtils.extractTokenFromCookie(httpHost, response);
        }
        return sst;
    }

    static String extractTokenFromBody(final HttpResponse response, final String entityName) throws IOException {
        final String responseBody = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
        return extractTokenFromBody(responseBody, entityName);
    }

    static String extractTokenFromBody(final String responseBody, final String entityName) throws IOException {
        final String anyNamedValues = "(?:\\s*\"\\w+\"\\s*\\:\\s*\"[\\w/]+\"\\s*,?\\s*)*";
        final String token = "\\s*\"token\"\\s*\\:\\s*\"(\\S+?)\"\\s*,?\\s*";
        final Pattern pattern = Pattern.compile("\\s*\\{\\s*\"" + entityName + "\"\\s*\\:\\s*\\{" + anyNamedValues + token + anyNamedValues + "\\}\\s*\\}\\s*");
        final Matcher matcher = pattern.matcher(responseBody);
        if (!matcher.matches()) {
            throw new GoodDataAuthException("Unable to login. Malformed response body: " + responseBody);
        }
        return matcher.group(1);
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

    /**
     * Builder for HTTP client instances.
     * Can be subclassed to create a builder for a subclass of {@link GoodDataHttpClient}.
     * @param <T> class to create instances of
     */
    public static abstract class Builder<T extends GoodDataHttpClient> {
        protected HttpClient httpClient;
        protected VerificationLevel verificationLevel = VerificationLevel.HEADER;
        protected String username;
        protected String password;
        protected String sst;
        protected HttpHost tokenHost;

        /**
         * Sets http client to be wrapped by the new {@link GoodDataHttpClient}.
         * @param httpClient client to be wrapped
         * @return this builder
         */
        public Builder<T> httpClient(final HttpClient httpClient) {
            notNull(httpClient);
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Sets username and password credentials.
         * @param username username to be used to renew SST
         * @param password password to be used to renew SST
         * @return this builder
         */
        public Builder<T> credentials(final String username, final String password) {
            notNull(username);
            notNull(password);
            this.username = username;
            this.password = password;
            return this;
        }

        /**
         * Sets SST.
         * @param sst SST to be used by the newly created {@link GoodDataHttpClient}
         * @return this builder
         */
        public Builder<T> sst(final String sst) {
            notNull(sst);
            this.sst = sst;
            return this;
        }

        /**
         * Sets verification level to be used by the new {@link GoodDataHttpClient}.
         * Default verification level is {@link VerificationLevel#HEADER}.
         * @param verificationLevel verification level
         * @return this builder
         */
        public Builder<T> verification(VerificationLevel verificationLevel) {
            notNull(verificationLevel);
            this.verificationLevel = verificationLevel;
            return this;
        }

        /**
         * Sets host to be used for renewing tokens by the new {@link GoodDataHttpClient}.
         * @param tokenHost host to be used for renewing tokens
         * @return this builder
         */
        public Builder<T> tokenHost(HttpHost tokenHost) {
            notNull(tokenHost);
            this.tokenHost = tokenHost;
            return this;
        }

        /**
         * Builds a new client instance.
         * @return new client
         */
        public abstract T build();
    }

    private static class GoodDataClientBuilder extends Builder<GoodDataHttpClient> {
        GoodDataClientBuilder(final String username, final String password) {
            credentials(username, password);
        }

        GoodDataClientBuilder(final String sst) {
            sst(sst);
        }

        public GoodDataHttpClient build() {
            return new GoodDataHttpClient(httpClient, username, password, sst, verificationLevel, tokenHost);
        }
    }
}
