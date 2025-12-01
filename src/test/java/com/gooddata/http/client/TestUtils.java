/*
 * (C) 2025 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Common utilities for testing
 */
abstract class TestUtils {

    /**
     * Executes GET on given host and path and asserts the response to given status
     *
     * @param client         client for execution
     * @param httpHost       host
     * @param path           path at host
     * @param expectedStatus status to assert
     * @throws IOException
     * @throws ParseException
     */
    static void performGet(HttpClient client, HttpHost httpHost, String path, int expectedStatus) throws IOException, ParseException {
        getForEntity(client, httpHost, path, expectedStatus);
    }

    /**
     * Executes GET on given host and path and asserts the response to given status
     *
     * @param client         client for execution
     * @param httpHost       host
     * @param path           path at host
     * @param expectedStatus status to assert
     * @return fetched entity string representation
     * @throws IOException
     * @throws ParseException
     */
    static String getForEntity(HttpClient client, HttpHost httpHost, String path, int expectedStatus) throws IOException, ParseException {
        HttpGet get = new HttpGet(path);
        try {
            get.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
            ClassicHttpResponse getProjectResponse = client.execute(httpHost, get);
            assertEquals(expectedStatus, getProjectResponse.getCode());
            return getProjectResponse.getEntity() == null ? null : EntityUtils.toString(getProjectResponse.getEntity());
        } finally {
            get.reset();
        }
    }

    static void logout(HttpClient client, HttpHost httpHost, String profile, int expectedStatus) throws IOException {
        final HttpDelete logout = new HttpDelete("/gdc/account/login/" + profile);
        try {
            logout.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
            ClassicHttpResponse logoutResponse = client.execute(httpHost, logout);
            assertEquals(expectedStatus, logoutResponse.getCode());
            EntityUtils.consume(logoutResponse.getEntity());
        } finally {
            logout.reset();
        }
    }

    static HttpClient createGoodDataClient(String login, String password, HttpHost host) {
        final HttpClient httpClient = HttpClientBuilder.create().build();
        final SSTRetrievalStrategy sstStrategy = new LoginSSTRetrievalStrategy(login, password);
        return new GoodDataHttpClient(httpClient, host, sstStrategy);
    }
}
