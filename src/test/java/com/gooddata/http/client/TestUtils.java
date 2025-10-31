/*
 * (C) 2022 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import java.io.IOException;
import org.apache.hc.client5.http.classic.methods.HttpGet;

abstract class TestUtils {

    // Calls a GET request using the GoodDataHttpClient and checks the response status.
    // This method delegates to getForEntity for actual execution and status assertion.
    static void performGet(GoodDataHttpClient client, HttpHost httpHost, String path, int expectedStatus) throws IOException, org.apache.hc.core5.http.HttpException {
        getForEntity(client, httpHost, path, expectedStatus);
    }

    // Executes a GET request and returns the response body as a String.
    // Uses basic execute method instead of ResponseHandler to avoid stream closure issues.
    static String getForEntity(GoodDataHttpClient client, HttpHost httpHost, String path, int expectedStatus) throws IOException, org.apache.hc.core5.http.HttpException {
        HttpGet get = new HttpGet(path);
        get.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());

        try (ClassicHttpResponse response = client.execute(httpHost, get, null)) {
            if (response.getCode() != expectedStatus) {
                throw new IOException("Unexpected status: " + response.getCode());
            }
            return response.getEntity() == null ? null : EntityUtils.toString(response.getEntity());
        }
    }


    // Executes a DELETE request (used for logout) and checks the response status.
    // Uses basic execute method instead of ResponseHandler to avoid stream closure issues.
    static void logout(GoodDataHttpClient client, HttpHost httpHost, String profile, int expectedStatus) throws IOException, org.apache.hc.core5.http.HttpException {
        org.apache.hc.client5.http.classic.methods.HttpDelete logout = new org.apache.hc.client5.http.classic.methods.HttpDelete("/gdc/account/login/" + profile);
        logout.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());

        try (ClassicHttpResponse response = client.execute(httpHost, logout, null)) {
            assertEquals(expectedStatus, response.getCode()); // Assert HTTP status code.
            EntityUtils.consume(response.getEntity());        // Ensure the entity is fully consumed and resources are released.
        }
    }

    // Factory method to create a GoodDataHttpClient instance with login/password auth.
    // Internally creates the underlying Apache HttpClient and wraps it.
    static GoodDataHttpClient createGoodDataClient(String login, String password, HttpHost host) {
        org.apache.hc.client5.http.classic.HttpClient httpClient = org.apache.hc.client5.http.impl.classic.HttpClients.createDefault();
        SSTRetrievalStrategy sstStrategy = new LoginSSTRetrievalStrategy(login, password);
        return new GoodDataHttpClient(httpClient, host, sstStrategy);
    }
}
