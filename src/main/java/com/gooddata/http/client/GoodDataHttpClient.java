/*
 * (C) 2022 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;
import static com.gooddata.http.client.LoginSSTRetrievalStrategy.LOGIN_URL;
import static org.apache.commons.lang3.Validate.notNull;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Http client with ability to handle GoodData authentication.
 * Fully migrated to Apache HttpClient 5.x "response handler" style.
 */
public class GoodDataHttpClient {
    private static final String TOKEN_URL = "/gdc/account/token";
    public static final String COOKIE_GDC_AUTH_TT = "cookie=GDCAuthTT";
    public static final String COOKIE_GDC_AUTH_SST = "cookie=GDCAuthSST";
    private volatile boolean tokenRefreshing = false;
    private final Object tokenRefreshMonitor = new Object();
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
        final ClassicHttpRequest originalRequest,
        final ClassicHttpResponse originalResponse,
        final HttpContext context) throws IOException, InterruptedException {

        if (originalResponse == null) {
            throw new IllegalStateException("httpClient.execute returned null! Check your mock configuration.");
        }

        final GoodDataChallengeType challenge = identifyGoodDataChallenge(originalResponse);

        if (challenge == GoodDataChallengeType.UNKNOWN) {
            return originalResponse;
        }

        EntityUtils.consume(originalResponse.getEntity());
        synchronized (tokenRefreshMonitor) {
            if (tokenRefreshing) {
                while (tokenRefreshing) {
                    try {
                        tokenRefreshMonitor.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while waiting for token refresh", e);
                    }
                }
                // After waiting, verify that tt was successfully obtained
                if (tt == null) {
                    throw new GoodDataAuthException("Token refresh completed but TT is still null");
                }
                final ClassicHttpRequest retryRequest = cloneRequestWithNewTT(originalRequest, tt);
                return this.httpClient.execute(httpHost, retryRequest, context, response -> copyResponseEntity(response));
            } else {
                tokenRefreshing = true;
            }
        }

        try {
            final Lock writeLock = rwLock.writeLock();
            writeLock.lock();
            try {
                boolean doSST = true;
                if (challenge == GoodDataChallengeType.TT && sst != null) {
                    boolean refreshed = refreshTt();
                    if (refreshed) {
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
        } finally {
            synchronized (tokenRefreshMonitor) {
                tokenRefreshing = false;
                tokenRefreshMonitor.notifyAll();
            }
        }

        final ClassicHttpRequest retryRequest = cloneRequestWithNewTT(originalRequest, tt);
        ClassicHttpResponse retryResponse = this.httpClient.execute(httpHost, retryRequest, context, response -> copyResponseEntity(response));

        if (retryResponse.getCode() == HttpStatus.SC_UNAUTHORIZED &&
            identifyGoodDataChallenge(retryResponse) != GoodDataChallengeType.UNKNOWN) {
            return retryResponse;
        }
        return retryResponse;
    }
 
    private ClassicHttpRequest cloneRequestWithNewTT(ClassicHttpRequest original, String newTT) throws IOException {
        ClassicHttpRequest copy;
        
        // Clone request based on method type
        switch (original.getMethod()) {
            case "GET":
                copy = new HttpGet(original.getRequestUri());
                break;
            case "POST":
                copy = cloneRequestWithEntity(
                    new org.apache.hc.client5.http.classic.methods.HttpPost(original.getRequestUri()),
                    original
                );
                break;
            case "PUT":
                copy = cloneRequestWithEntity(
                    new org.apache.hc.client5.http.classic.methods.HttpPut(original.getRequestUri()),
                    original
                );
                break;
            case "PATCH":
                copy = cloneRequestWithEntity(
                    new org.apache.hc.client5.http.classic.methods.HttpPatch(original.getRequestUri()),
                    original
                );
                break;
            case "DELETE":
                copy = new org.apache.hc.client5.http.classic.methods.HttpDelete(original.getRequestUri());
                break;
            case "HEAD":
                copy = new org.apache.hc.client5.http.classic.methods.HttpHead(original.getRequestUri());
                break;
            case "OPTIONS":
                copy = new org.apache.hc.client5.http.classic.methods.HttpOptions(original.getRequestUri());
                break;
            default:
                throw new UnsupportedOperationException("Unsupported HTTP method: " + original.getMethod());
        }
        
        // Copy original headers
        for (Header header : original.getHeaders()) {
            copy.addHeader(header.getName(), header.getValue());
        }
        
        // Set the new TT
        copy.addHeader(TT_HEADER, newTT);
        return copy;
    }

    /**
     * Helper method to clone request entity safely, handling both repeatable and non-repeatable entities.
     * For non-repeatable entities, buffers the content to allow reuse.
     */
    private <T extends ClassicHttpRequest> T cloneRequestWithEntity(T target, ClassicHttpRequest source) throws IOException {
        if (!(source instanceof org.apache.hc.core5.http.HttpEntityContainer)) {
            return target;
        }
        
        org.apache.hc.core5.http.HttpEntity entity = 
            ((org.apache.hc.core5.http.HttpEntityContainer) source).getEntity();
        
        if (entity == null) {
            return target;
        }
        
        // Check if entity is repeatable - if so, we can reuse it directly
        if (entity.isRepeatable()) {
            if (target instanceof org.apache.hc.core5.http.HttpEntityContainer) {
                ((org.apache.hc.core5.http.HttpEntityContainer) target).setEntity(entity);
            }
        } else {
            // Entity is not repeatable - buffer it for reuse
            log.debug("Buffering non-repeatable entity for retry");
            byte[] content = EntityUtils.toByteArray(entity);
            String contentTypeStr = entity.getContentType();
            ContentType contentType = contentTypeStr != null ? 
                ContentType.parseLenient(contentTypeStr) : ContentType.DEFAULT_BINARY;
            
            ByteArrayEntity bufferedEntity = new ByteArrayEntity(content, contentType);
            if (target instanceof org.apache.hc.core5.http.HttpEntityContainer) {
                ((org.apache.hc.core5.http.HttpEntityContainer) target).setEntity(bufferedEntity);
            }
        }
        
        return target;
    }

    private boolean refreshTt() throws IOException {
        log.debug("Obtaining TT");
        final HttpGet request = new HttpGet(TOKEN_URL);
        try {

            request.addHeader(SST_HEADER, sst);

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
        // Using write lock for all requests to prevent deadlock scenarios where:
        // 1. Thread A holds read lock and calls handleResponse (needs write lock)
        // 2. Thread B waits for write lock to refresh tokens
        // 3. Deadlock occurs as Thread A can't upgrade from read to write lock
        // Trade-off: Serializes all requests but ensures thread safety during token refresh
        final Lock lock = rwLock.writeLock();
        lock.lock();
        try {

            // --- PATCH: Always check logout even if TT is null, if it's a logout request ---
            if (isLogoutRequest(target, request)) {
                try {

                    sstStrategy.logout(httpClient, target, request.getRequestUri(), sst, tt);
                    tt = null;
                    sst = null;
                    // Return a dummy response for logout success
                    return new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "Logout successful");
                } catch (GoodDataLogoutException e) {
                    throw new GoodDataHttpStatusException(e.getStatusCode(), e.getStatusText());
                }
            }
            // --- END PATCH ---

            if (tt != null) {
                request.addHeader(TT_HEADER, tt);
            }

            ClassicHttpResponse resp = this.httpClient.execute(target, request, context, response -> copyResponseEntity(response));

            if (resp.getCode() == HttpStatus.SC_UNAUTHORIZED) {
                // 👇 Proper handling of InterruptedException
                try {
                    return handleResponse(target, request, resp, context);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Preserve interrupt status
                    throw new IOException("Interrupted while handling authentication challenge", e);
                }
            }

            return resp;

        } finally {
            lock.unlock();
        }
    }

    public ClassicHttpResponse execute(HttpHost target, ClassicHttpRequest request) throws IOException {
        return execute(target, request, null);
    }

    public <T> T execute(HttpHost target, ClassicHttpRequest request, HttpContext context,
                        HttpClientResponseHandler<? extends T> responseHandler) throws IOException, org.apache.hc.core5.http.HttpException {
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

    /**
     * Helper method to copy response entity to avoid stream closure issues.
     * Returns a new response with the same properties but a copied entity.
     */
    private ClassicHttpResponse copyResponseEntity(ClassicHttpResponse response) throws IOException {
        if (response.getEntity() == null) {
            return response;
        }
        
        // Copy the entity content
        byte[] content = EntityUtils.toByteArray(response.getEntity());
        String contentTypeStr = response.getEntity().getContentType();
        ContentType contentType = contentTypeStr != null ? 
            ContentType.parseLenient(contentTypeStr) : ContentType.DEFAULT_BINARY;
        
        // Create a new response with copied entity
        BasicClassicHttpResponse newResponse = new BasicClassicHttpResponse(response.getCode(), response.getReasonPhrase());
        for (Header header : response.getHeaders()) {
            newResponse.addHeader(header);
        }
        newResponse.setEntity(new ByteArrayEntity(content, contentType));
        
        return newResponse;
    }
}