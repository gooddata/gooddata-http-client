package com.gooddata.http.client;

import static com.gooddata.http.client.TestUtils.performGet;

import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

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
        final HttpClient httpClient = HttpClientBuilder.create().build();
        final SSTRetrievalStrategy sstStrategy = new LoginSSTRetrievalStrategy(login, password);
        final HttpClient client = new GoodDataHttpClient(httpClient, httpHost, sstStrategy);

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


}
