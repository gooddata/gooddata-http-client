/*
 * (C) 2022 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

// HttpClient 5 imports!
import static com.gooddata.http.client.LoginSSTRetrievalStrategy.LOGIN_URL;
import static org.apache.commons.lang3.Validate.notNull;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.Header;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Http client with ability to handle GoodData authentication.
 * Fully migrated to Apache HttpClient 5.x "response handler" style.
 */
public class GoodDataHttpClient {

    private static final String TOKEN_URL = "/gdc/account/token";
    public static final String COOKIE_GDC_AUTH_TT = "cookie=GDCAuthTT";
    public static final String COOKIE_GDC_AUTH_SST = "cookie=GDCAuthSST";

    static final String TT_HEADER = "X-GDC-AuthTT";
    static final String SST_HEADER = "X-GDC-AuthSST";

    private enum GoodDataChallengeType {
        SST, TT, UNKNOWN
    }

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final HttpClient httpClient;
    private final SSTRetrievalStrategy sstStrategy;
    private final HttpHost authHost;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock authLock = new ReentrantLock();

    private String sst;
    private String tt;

    // Constructors remain unchanged (just update parameter types to HttpClient 5.x classes if needed)

    public GoodDataHttpClient(final HttpClient httpClient, final HttpHost authHost, final SSTRetrievalStrategy sstStrategy) {
        notNull(httpClient);
        notNull(authHost, "HTTP host cannot be null");
        notNull(sstStrategy);
        this.httpClient = httpClient;
        this.authHost = authHost;
        this.sstStrategy = sstStrategy;
    }
    

    public GoodDataHttpClient(final HttpHost authHost, final SSTRetrievalStrategy sstStrategy) {
        this(org.apache.hc.client5.http.impl.classic.HttpClients.createDefault(), authHost, sstStrategy);
    }

    /**
     * Identify the type of GoodData authentication challenge from the response.
     */
    private GoodDataChallengeType identifyGoodDataChallenge(final ClassicHttpResponse response) {
        if (response.getCode() == HttpStatus.SC_UNAUTHORIZED) {
            Header[] headers = response.getHeaders("WWW-Authenticate");
            if (headers != null) {
                for (Header header : headers) {
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

    /**
     * Handles the authentication challenge and returns a refreshed response.
     */
    private ClassicHttpResponse handleResponse(
            final HttpHost httpHost,
            final ClassicHttpRequest request,
            final ClassicHttpResponse originalResponse,
            final HttpContext context) throws IOException {

        if (originalResponse == null) {
            throw new IllegalStateException("httpClient.execute returned null! Check your mock configuration.");
        }
        final GoodDataChallengeType challenge = identifyGoodDataChallenge(originalResponse);
        if (challenge == GoodDataChallengeType.UNKNOWN) {
            return originalResponse;
        }
        EntityUtils.consume(originalResponse.getEntity());

        try {
            if (authLock.tryLock()) {
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
                } finally {
                    writeLock.unlock();
                }
            } else {
                authLock.lock();
            }
        } finally {
            authLock.unlock();
        }
        // New style: use response handler, response will be automatically released
        return this.httpClient.execute(httpHost, request, context, response -> response);
    }

    /**
     * Refreshes the temporary token (TT) using SST.
     */
    private boolean refreshTt() throws IOException {
        log.debug("Obtaining TT");
        final HttpGet request = new HttpGet(TOKEN_URL);
        try {
            request.setHeader(SST_HEADER, sst);
            // Use response handler for token extraction
            return httpClient.execute(authHost, request, (HttpContext) null, response -> {
                int status = response.getCode();
                switch (status) {
                    case HttpStatus.SC_OK:
                        tt = TokenUtils.extractTT(response);
                        return true;
                    case HttpStatus.SC_UNAUTHORIZED:
                        return false;
                    default:
                        throw new GoodDataAuthException("Unable to obtain TT, HTTP status: " + status);
                }
            });
        } finally {
            request.reset();
        }
    }

    /**
     * Main public execute method: new style, always uses response handler.
     */
    public ClassicHttpResponse execute(HttpHost target, ClassicHttpRequest request, HttpContext context) throws IOException {
        notNull(request, "Request can't be null");
        final boolean logoutRequest = isLogoutRequest(target, request);
        final Lock lock = logoutRequest ? rwLock.writeLock() : rwLock.readLock();
        lock.lock();

        try {
            if (tt != null) {
                request.setHeader(TT_HEADER, tt);

                if (logoutRequest) {
                    try {
                        sstStrategy.logout(httpClient, target, request.getRequestUri(), sst, tt);
                        tt = null;
                        sst = null;
                        // Return a dummy response for logout success
                        return new org.apache.hc.core5.http.message.BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "Logout successful");
                    } catch (GoodDataLogoutException e) {
                        return new org.apache.hc.core5.http.message.BasicClassicHttpResponse(e.getStatusCode(), e.getStatusText());
                    }
                }
            }
            // Always use response handler: never return or expect CloseableHttpResponse anymore!
            ClassicHttpResponse resp = this.httpClient.execute(
                    target,
                    request,
                    context,
                    response -> response // just return the response
            );
            return handleResponse(target, request, resp, context);
        } finally {
            lock.unlock();
        }
    }


    public ClassicHttpResponse execute(HttpHost target, ClassicHttpRequest request) throws IOException {
         return httpClient.execute(target, request, (HttpContext) null, response -> response);
    }


    public <T> T execute(HttpHost target, ClassicHttpRequest request, HttpContext context,
                        HttpClientResponseHandler<? extends T> responseHandler) throws IOException {
        return httpClient.execute(target, request, context, responseHandler);
    }
    /**
     * Util for logout request check.
     */
    private boolean isLogoutRequest(HttpHost target, ClassicHttpRequest request) {
        return authHost.equals(target)
                && "DELETE".equals(request.getMethod())
                && URI.create(request.getRequestUri()).getPath().startsWith(LOGIN_URL);
    }
}
