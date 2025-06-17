/*
 * (C) 2022 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import static com.gooddata.http.client.GoodDataHttpClient.SST_HEADER;
import static com.gooddata.http.client.GoodDataHttpClient.TT_HEADER;
import static java.lang.String.format;
import static org.apache.commons.lang3.Validate.notEmpty;
import static org.apache.commons.lang3.Validate.notNull;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpStatus;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;






import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * This strategy obtains super-secure token via login and password.
 */
public class LoginSSTRetrievalStrategy implements SSTRetrievalStrategy {

    private static final String X_GDC_REQUEST_HEADER_NAME = "X-GDC-REQUEST";

    public static final String LOGIN_URL = "/gdc/account/login";

    /** SST and TT must be present in the HTTP header. */
    private static final int VERIFICATION_LEVEL = 2;

    private Logger log = LoggerFactory.getLogger(getClass());

    private final String login;

    private final String password;

    private final HttpHost httpHost;

    /**
     * Construct object.
     * @deprecated Use {@link #LoginSSTRetrievalStrategy(String, String)}}
     * @param httpClient HTTP client
     * @param httpHost http host
     * @param login user login
     * @param password user password
     */
    @Deprecated
    public LoginSSTRetrievalStrategy(final HttpClient httpClient, final HttpHost httpHost, final String login, final String password) {
        notNull(httpHost, "HTTP host cannot be null");
        notNull(login, "Login cannot be null");
        notNull(password, "Password cannot be null");
        this.login = login;
        this.password = password;
        this.httpHost = httpHost;
    }

    /**
     * Construct object.
     * @param login user login
     * @param password user password
     */
    public LoginSSTRetrievalStrategy(final String login, final String password) {
        notNull(login, "Login cannot be null");
        notNull(password, "Password cannot be null");
        this.login = login;
        this.password = password;
        this.httpHost = null;
    }

    HttpHost getHttpHost() {
        return httpHost;
    }

    @Override
    public String obtainSst(final HttpClient httpClient, final HttpHost httpHost) throws IOException {
        notNull(httpClient, "client can't be null");
        notNull(httpHost, "host can't be null");

        log.debug("Obtaining SST");
        final HttpPost postLogin = new HttpPost(LOGIN_URL);
        try {
            final String loginJson = JsonUtils.createLoginJson(login, password, VERIFICATION_LEVEL);
            postLogin.setEntity(new StringEntity(loginJson, ContentType.APPLICATION_JSON));

            HttpClientResponseHandler<String> responseHandler = response -> {
                int status = response.getCode();
                if (status != HttpStatus.SC_OK) {
                    final String message = getMessage(response);
                    log.info(message);
                    throw new GoodDataAuthException(message);
                }
                // todo TT is present at response as well - extract it to save one HTTP call
                
                String sst = TokenUtils.extractSST(response);

                return sst;
            };

            return httpClient.execute(httpHost, postLogin, responseHandler);

        } catch (GoodDataAuthException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to obtain SST", e);
        } finally {
            postLogin.reset();
        }
    }


    private String getMessage(final ClassicHttpResponse response) throws IOException {
        // Try to extract the request ID from the response headers
        final Header requestIdHeader = response.getFirstHeader(X_GDC_REQUEST_HEADER_NAME);
        final String requestId = requestIdHeader != null ? requestIdHeader.getValue() : null;

        // Try to read the response entity (body)
        final HttpEntity responseEntity = response.getEntity();
        String reason = null;
        try {
            reason = responseEntity != null ? EntityUtils.toString(responseEntity) : null;
        } catch (Exception e) {
            reason = "Failed to parse response body: " + e.getMessage();
        }

        // Return a formatted error message with the reason, HTTP status code, and request ID
        return format(
            "Unable to login reason='%s'. Request tracking details httpStatus=%s requestId=%s",
            reason, response.getCode(), requestId
        );
    }

    @Override
    public void logout(final HttpClient httpClient, final HttpHost httpHost, final String url, final String sst, final String tt)
            throws IOException, GoodDataLogoutException {

        notNull(httpClient, "client can't be null");
        notNull(httpHost, "host can't be null");
        notEmpty(url, "url can't be empty");
        notEmpty(sst, "SST can't be empty");
        notEmpty(tt, "TT can't be empty");

        log.debug("performing logout");
        final HttpDelete request = new HttpDelete(url);
        try {
            request.addHeader(SST_HEADER, sst);
            request.addHeader(TT_HEADER, tt);

            org.apache.hc.core5.http.io.HttpClientResponseHandler<Void> handler = response -> {
                if (response.getCode() != HttpStatus.SC_NO_CONTENT) {
                    throw new IOException(new GoodDataLogoutException("Logout unsuccessful using http",
                            response.getCode(), response.getReasonPhrase()));
                }
                return null;
            };

            try {
                httpClient.execute(httpHost, request, handler);
            } catch (IOException e) {
                if (e.getCause() instanceof GoodDataLogoutException) {
                    throw (GoodDataLogoutException) e.getCause();
                }
                throw e;
            }
        } finally {
            request.reset();
        }
    }


    void setLogger(Logger log) {
        this.log = log;
    }

}
