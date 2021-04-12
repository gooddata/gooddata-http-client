/*
 * (C) 2021 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import static com.gooddata.http.client.TestUtils.createGoodDataClient;
import static com.gooddata.http.client.TestUtils.getForEntity;
import static com.gooddata.http.client.TestUtils.logout;
import static com.gooddata.http.client.TestUtils.performGet;
import static org.junit.Assert.assertEquals;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Acceptance tests. Requires GoodData credentials.<br/>
 * <code>mvn -Pat clean verify -DGDC_LOGIN=user@email.com -DGDC_PASSWORD=password [-DGDC_BACKEND=<backend host>]</code>
 */
public class GoodDataHttpClientAT {

    private static final String GDC_PROJECTS_PATH = "/gdc/projects";

    private final String login = System.getProperty("GDC_LOGIN");
    private final String password = System.getProperty("GDC_PASSWORD");
    private final HttpHost httpHost = new HttpHost(System.getProperty("GDC_BACKEND", "secure.gooddata.com"), 443, "https");

    @Test
    public void gdcLogin() throws IOException {
        final HttpClient client = createGoodDataClient(login, password, httpHost);

        performGet(client, httpHost, GDC_PROJECTS_PATH, HttpStatus.SC_OK);
    }

    @Test
    public void gdcSstSimple() throws IOException {
        final HttpClient httpClient = HttpClientBuilder.create().build();

        final LoginSSTRetrievalStrategy loginSSTRetrievalStrategy = new LoginSSTRetrievalStrategy(login, password);
        final String sst = loginSSTRetrievalStrategy.obtainSst(httpClient, httpHost);

        final SSTRetrievalStrategy sstStrategy = new SimpleSSTRetrievalStrategy(sst);
        final HttpClient client = new GoodDataHttpClient(httpClient, httpHost, sstStrategy);

        performGet(client, httpHost, GDC_PROJECTS_PATH, HttpStatus.SC_OK);
    }

    private static final Pattern profilePattern = Pattern.compile("\"/gdc/account/profile/([^\"]+)\"");

    @Test
    public void gdcLogout() throws IOException {
        final HttpClient client = createGoodDataClient(login, password, httpHost);
        final String response = getForEntity(client, httpHost, "/gdc/account/profile/current", HttpStatus.SC_OK);
        final Matcher matcher = profilePattern.matcher(response);
        matcher.find();
        final String profile = matcher.group(1);

        logout(client, httpHost, profile, HttpStatus.SC_NO_CONTENT);
    }


}
