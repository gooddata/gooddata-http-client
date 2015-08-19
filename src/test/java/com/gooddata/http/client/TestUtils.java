package com.gooddata.http.client;

import static org.junit.Assert.assertEquals;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
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
        HttpGet getProject = new HttpGet(path);
        try {
            getProject.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
            HttpResponse getProjectResponse = client.execute(httpHost, getProject);
            assertEquals(expectedStatus, getProjectResponse.getStatusLine().getStatusCode());
            EntityUtils.consume(getProjectResponse.getEntity());
        } finally {
            getProject.releaseConnection();
        }
    }
}
