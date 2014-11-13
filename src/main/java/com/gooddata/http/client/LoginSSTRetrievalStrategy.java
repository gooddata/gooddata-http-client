/*
 * Copyright (C) 2007-2013, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.cookie.SM;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.cookie.BestMatchSpec;

import java.io.IOException;
import java.util.List;

import static org.apache.commons.lang.Validate.notNull;

/**
 * This strategy obtains super-secure token via login and password.
 */
public class LoginSSTRetrievalStrategy implements SSTRetrievalStrategy {

    public static final String LOGIN_URL = "/gdc/account/login";

    private final Log log = LogFactory.getLog(getClass());

    private final String login;

    private final String password;

    private final HttpHost httpHost;

    private final HttpClient httpClient;

    /**
     * Construct object.
     * @param httpClient HTTP client
     * @param httpHost http host
     * @param login user login
     * @param password user password
     */
    public LoginSSTRetrievalStrategy(final HttpClient httpClient, final HttpHost httpHost, final String login, final String password) {
        notNull(httpClient, "HTTP Client cannot be null");
        notNull(httpHost, "HTTP host cannot be null");
        notNull(login, "Login cannot be null");
        notNull(password, "Password cannot be null");
        this.login = login;
        this.password = password;
        this.httpHost = httpHost;
        this.httpClient = httpClient;
    }

    @Override
    public String obtainSst() throws IOException {
        log.debug("Obtaining STT");
        final HttpPost postLogin = new HttpPost(LOGIN_URL);
        try {
            final HttpEntity requestEntity = new StringEntity(createLoginJson(login, password), ContentType.APPLICATION_JSON);
            postLogin.setEntity(requestEntity);
            postLogin.setHeader("Accept", ContentType.APPLICATION_JSON.toString());
            final HttpResponse response = httpClient.execute(httpHost, postLogin);
            int status = response.getStatusLine().getStatusCode();
            if (status != HttpStatus.SC_OK) {
                throw new GoodDataAuthException("Unable to login: " + status);
            }
            final String sst = extractSST(response);
            if (sst == null) {
                throw new GoodDataAuthException("Unable to login. Missing SST Set-Cookie header.");
            }
            return sst;
        } catch (MalformedCookieException e) {
            throw new GoodDataAuthException("Unable to login. Malformed Set-Cookie header.");
        } finally {
            postLogin.releaseConnection();
        }
    }

    private String createLoginJson(final String login, final String password) {
        return "{\"postUserLogin\":{\"login\":\"" + StringEscapeUtils.escapeJavaScript(login) +
                "\",\"password\":\"" + StringEscapeUtils.escapeJavaScript(password) + "\",\"remember\":0}}";
    }

    private String extractSST(final HttpResponse response) throws MalformedCookieException {
        String sst = null;
        final CookieSpec cookieSpec = new BestMatchSpec();
        final CookieOrigin cookieOrigin = new CookieOrigin(httpHost.getHostName(), httpHost.getPort(), "/gdc/account", true);
        for (Header header : response.getHeaders(SM.SET_COOKIE)) {
            final List<Cookie> cookies = cookieSpec.parse(header, cookieOrigin);
            if (cookies.size() > 0 && CookieUtils.SST_COOKIE_NAME.equals(cookies.get(0).getName())) {
                sst = cookies.get(0).getValue();
                break;
            }
        }
        return sst;
    }
}
