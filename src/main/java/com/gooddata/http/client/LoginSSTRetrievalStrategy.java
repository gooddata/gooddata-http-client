/*
 * (C) 2025 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.StatusLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.gooddata.http.client.GoodDataHttpClient.SST_HEADER;
import static com.gooddata.http.client.GoodDataHttpClient.TT_HEADER;
import static java.lang.String.format;
import static org.apache.commons.lang3.Validate.notEmpty;
import static org.apache.commons.lang3.Validate.notNull;

/**
 * This strategy obtains super-secure token via login and password.
 */
public class LoginSSTRetrievalStrategy implements SSTRetrievalStrategy {

    public static final String LOGIN_URL = "/gdc/account/login";
    private static final String X_GDC_REQUEST_HEADER_NAME = "X-GDC-REQUEST";
    /**
     * SST and TT must be present in the HTTP header.
     */
    private static final int VERIFICATION_LEVEL = 2;
    private final String login;
    private final String password;
    private final HttpHost httpHost;
    private Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Construct object.
     *
     * @param httpClient HTTP client
     * @param httpHost   http host
     * @param login      user login
     * @param password   user password
     * @deprecated Use {@link #LoginSSTRetrievalStrategy(String, String)}}
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
     *
     * @param login    user login
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
        HttpResponse response = null;
        try {
            final String loginJson = JsonUtils.createLoginJson(login, password, VERIFICATION_LEVEL);
            postLogin.setEntity(new StringEntity(loginJson, ContentType.APPLICATION_JSON));

            response = httpClient.execute(httpHost, postLogin);
            int status = response.getCode();
            if (status != HttpStatus.SC_OK) {
                final String message = getMessage(response);
                log.info(message);
                throw new GoodDataAuthException(message);
            }

            // todo TT is present at response as well - extract it to save one HTTP call
            return TokenUtils.extractSST(response);
        } catch (ParseException e) {
            IOException ioException = new IOException("Failed to parse HTTP response during SST retrieval: " + e.getMessage(), e);
            throw ioException;
        } finally {
            if (response instanceof ClassicHttpResponse) {
                EntityUtils.consumeQuietly(((ClassicHttpResponse) response).getEntity());
            }
            postLogin.reset();
        }
    }

    private String getMessage(final HttpResponse response) throws IOException, ParseException {
        final Header requestIdHeader = response.getFirstHeader(X_GDC_REQUEST_HEADER_NAME);
        final String requestId = requestIdHeader != null ? requestIdHeader.getValue() : null;

        final HttpEntity responseEntity = ((ClassicHttpResponse) response).getEntity();
        final String reason = responseEntity != null ? EntityUtils.toString(responseEntity) : null;

        return format("Unable to login reason='%s'. Request tracking details httpStatus=%s requestId=%s",
                reason, response.getCode(), requestId);
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
        HttpResponse response = null;
        try {
            request.setHeader(SST_HEADER, sst);
            request.setHeader(TT_HEADER, tt);
            response = httpClient.execute(httpHost, request);
            final StatusLine statusLine = new StatusLine(response);
            if (statusLine.getStatusCode() != HttpStatus.SC_NO_CONTENT) {
                throw new GoodDataLogoutException("Logout unsuccessful using http",
                        statusLine.getStatusCode(), statusLine.getReasonPhrase());
            }
        } finally {
            if (response instanceof ClassicHttpResponse) {
                EntityUtils.consumeQuietly(((ClassicHttpResponse) response).getEntity());
            }
            request.reset();
        }
    }

    /**
     * Fot tests only
     */
    void setLogger(Logger log) {
        this.log = log;
    }

}
