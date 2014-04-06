/*
 * Copyright (C) 2007-2014, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;

import static org.apache.commons.lang.Validate.notNull;

/**
 * This strategy obtains super-secure token via login and password.
 * @deprecated Use static methods of {@link com.gooddata.http.client.GoodDataHttpClient} to create new clients. You can subclass
 * {@link GoodDataHttpClient} directly to override the default SST retrieval strategies.
 */
@SuppressWarnings({"deprecation", "UnusedDeclaration"})
public class LoginSSTRetrievalStrategy implements SSTRetrievalStrategy {
    public static final String LOGIN_URL = "/gdc/account/login";
    public static final String SST_ENTITY = "userLogin";

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
    public String obtainSst() {
        return GoodDataHttpClient.obtainSst(log, httpClient, httpHost, login, password, VerificationLevel.COOKIE);
    }
}
