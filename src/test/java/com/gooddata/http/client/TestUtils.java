/*
 * (C) 2022 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import java.io.IOException;
import org.apache.hc.client5.http.classic.methods.HttpGet;

abstract class TestUtils {

    // Calls a GET request using the GoodDataHttpClient and checks the response status.
    // This method delegates to getForEntity for actual execution and status assertion.
    static void performGet(GoodDataHttpClient client, HttpHost httpHost, String path, int expectedStatus) throws IOException {
        getForEntity(client, httpHost, path, expectedStatus);
    }

    // Executes a GET request and returns the response body as a String.
    // Uses a lambda as a ResponseHandler to ensure that the HTTP response is closed automatically.
    // This is the recommended resource-safe way in HttpClient 5.x.
    static String getForEntity(GoodDataHttpClient client, HttpHost httpHost, String path, int expectedStatus) throws IOException {
        HttpGet get = new HttpGet(path);
        get.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());

        return client.execute(httpHost, get, null, response -> {
            if (response.getCode() != expectedStatus) {
                throw new IOException("Unexpected status: " + response.getCode());
            }
            return response.getEntity() == null ? null : EntityUtils.toString(response.getEntity());
        });
    }


    // Executes a DELETE request (used for logout) and checks the response status.
    // Also uses lambda ResponseHandler for safe connection/resource handling.
    static void logout(GoodDataHttpClient client, HttpHost httpHost, String profile, int expectedStatus) throws IOException {
        org.apache.hc.client5.http.classic.methods.HttpDelete logout = new org.apache.hc.client5.http.classic.methods.HttpDelete("/gdc/account/login/" + profile);
        logout.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());

        client.execute(httpHost, logout, null, response -> {
            assertEquals(expectedStatus, response.getCode()); // Assert HTTP status code.
            EntityUtils.consume(response.getEntity());        // Ensure the entity is fully consumed and resources are released.
            return null;                                     // No result needed for void
        });
    }

    // Factory method to create a GoodDataHttpClient instance with login/password auth.
    // Internally creates the underlying Apache HttpClient and wraps it.
    static GoodDataHttpClient createGoodDataClient(String login, String password, HttpHost host) {
        org.apache.hc.client5.http.classic.HttpClient httpClient = org.apache.hc.client5.http.impl.classic.HttpClients.createDefault();
        SSTRetrievalStrategy sstStrategy = new LoginSSTRetrievalStrategy(login, password);
        return new GoodDataHttpClient(httpClient, host, sstStrategy);
    }
}
