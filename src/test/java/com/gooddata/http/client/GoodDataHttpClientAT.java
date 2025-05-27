/*
 * (C) 2022 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import static com.gooddata.http.client.TestUtils.createGoodDataClient;
import static com.gooddata.http.client.TestUtils.getForEntity;
import static com.gooddata.http.client.TestUtils.logout;
import static com.gooddata.http.client.TestUtils.performGet;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoodDataHttpClientAT {

    private static final String GDC_PROJECTS_PATH = "/gdc/projects";

    private final String login = System.getProperty("GDC_LOGIN");
    private final String password = System.getProperty("GDC_PASSWORD");
    private final HttpHost httpHost = new HttpHost("https", System.getProperty("GDC_BACKEND", "secure.gooddata.com"), 443);

    @Test
    public void gdcLogin() throws IOException {
        // Modern style: use TestUtils.createGoodDataClient, returns a GoodDataHttpClient wrapper
        final GoodDataHttpClient client = createGoodDataClient(login, password, httpHost);
        // performGet expects GoodDataHttpClient, uses execute() with a lambda (see TestUtils)
        performGet(client, httpHost, GDC_PROJECTS_PATH, HttpStatus.SC_OK);
    }

    @Test
    public void gdcSstSimple() throws IOException {
        final HttpClient httpClient = HttpClients.createDefault();
        final LoginSSTRetrievalStrategy loginSSTRetrievalStrategy = new LoginSSTRetrievalStrategy(login, password);
        final String sst = loginSSTRetrievalStrategy.obtainSst(httpClient, httpHost);

        final SSTRetrievalStrategy sstStrategy = new SimpleSSTRetrievalStrategy(sst);
        final GoodDataHttpClient client = new GoodDataHttpClient(httpClient, httpHost, sstStrategy);

        performGet(client, httpHost, GDC_PROJECTS_PATH, HttpStatus.SC_OK);
    }


    private static final Pattern profilePattern = Pattern.compile("\"/gdc/account/profile/([^\"]+)\"");

    @Test
    public void gdcLogout() throws IOException {
        final GoodDataHttpClient client = createGoodDataClient(login, password, httpHost);
        final String response = getForEntity(client, httpHost, "/gdc/account/profile/current", HttpStatus.SC_OK);
        final Matcher matcher = profilePattern.matcher(response);
        matcher.find();
        final String profile = matcher.group(1);

        logout(client, httpHost, profile, HttpStatus.SC_NO_CONTENT);
    }
}

