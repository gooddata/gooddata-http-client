/*
 * (C) 2021 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

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

import java.io.IOException;

/**
 * Common utilities for testing
 */
abstract class TestUtils {

    /**
     * Executes GET on given host and path and asserts the response to given status
     *
     * @param client client for execution
     * @param httpHost host
     * @param path path at host
     * @param expectedStatus status to assert
     * @throws IOException
     */
    static void performGet(HttpClient client, HttpHost httpHost, String path, int expectedStatus) throws IOException {
        getForEntity(client, httpHost, path, expectedStatus);
    }

    /**
     * Executes GET on given host and path and asserts the response to given status
     *
     * @param client client for execution
     * @param httpHost host
     * @param path path at host
     * @param expectedStatus status to assert
     * @throws IOException
     * @return fetched entity string representation
     */
    static String getForEntity(HttpClient client, HttpHost httpHost, String path, int expectedStatus) throws IOException {
        HttpGet get = new HttpGet(path);
        try {
            get.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
            HttpResponse getProjectResponse = client.execute(httpHost, get);
            assertEquals(expectedStatus, getProjectResponse.getStatusLine().getStatusCode());
            return getProjectResponse.getEntity() == null ? null : EntityUtils.toString(getProjectResponse.getEntity());
        } finally {
            get.reset();
        }
    }

    static void logout(HttpClient client, HttpHost httpHost, String profile, int expectedStatus) throws IOException {
        final HttpDelete logout = new HttpDelete("/gdc/account/login/" + profile);
        try {
            logout.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
            HttpResponse logoutResponse = client.execute(httpHost, logout);
            assertEquals(expectedStatus, logoutResponse.getStatusLine().getStatusCode());
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
