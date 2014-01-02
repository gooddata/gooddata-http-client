/*
 * Copyright (C) 2007-2013, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;


import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;

import static net.jadler.Jadler.closeJadler;
import static net.jadler.Jadler.initJadler;
import static net.jadler.Jadler.onRequest;
import static net.jadler.Jadler.port;
import static org.junit.Assert.assertEquals;

public class GoodDataHttpClientIntegrationTest {

    private static final String GDC_TOKEN_URL = "/gdc/account/token";
    public static final String GDC_ACCOUNT_URL = "/gdc/account";
    public static final String GDC_LOGIN_URL = "/gdc/account/login";
    public static final String GDC_PROJECTS_URL = "/gdc/projects";
    public static final String GDC_URL = "/gdc";

    private final String login = System.getProperty("GDC_LOGIN");
    private final String password = System.getProperty("GDC_PASSWORD");
    private final String sst = System.getProperty("GDC_SST");
    final HttpHost httpHost= new HttpHost("secure.gooddata.com", 443, "https");

    private String jadlerLogin;
    private String jadlerPassword;
    private HttpHost jadlerHost;

    @Before
    public void setUp() {
        initJadler();

        jadlerLogin = "user@email.com";
        jadlerPassword = "top secret";
        jadlerHost = new HttpHost("localhost", port(), "http");
    }

    @After
    public void tearDown() {
        closeJadler();
    }

    @Test(expected = GoodDataAuthException.class)
    public void getProjectsBadLogin() throws IOException {
        onRequest()
                .havingMethodEqualTo("GET")
                .havingURIEqualTo(GDC_PROJECTS_URL)
                .havingHeaderEqualTo("Accept", "application/json")
        .respond()
                .withStatus(401)
                .withHeader("WWW-Authenticate", "GoodData realm=\"GoodData API\" cookie=GDCAuthTT")
                .withBody("<html><head><title>401 Authorization Required</title></head><body><p>This server could not verify that you are authorized to access the document requested.  Either you supplied the wrong credentials (e.g., bad password), or your browser doesn't understand how to supply the credentials required.Please see <a href=\"http://docs.gooddata.apiary.io/#login\">Authenticating to the GoodData API</a> for details.</p></body></html>")
                .withEncoding(Charset.forName("UTF-8"))
                .withContentType("application/json; charset=UTF-8");

        onRequest()
                .havingMethodEqualTo("GET")
                .havingURIEqualTo(GDC_TOKEN_URL)
        .respond()
                .withStatus(401)
                .withHeader("WWW-Authenticate", "GoodData realm=\"GoodData API\" cookie=GDCAuthSST")
                .withBody("{\"parameters\":[],\"component\":\"Account::Token\",\"message\":\"/gdc/account/login\"}")
                .withContentType("application/json");

        onRequest()
                .havingMethodEqualTo("POST")
                .havingURIEqualTo(GDC_LOGIN_URL)
        .respond()
                .withStatus(401)
                .withHeader("WWW-Authenticate", "GoodData realm=\"GoodData API\"")
                .withBody("{\"parameters\":[],\"component\":\"Account::Login::AuthShare\",\"message\":\"Bad Login or Password!\"}")
                .withContentType("application/json");

        final HttpClient httpClient = HttpClientBuilder.create().build();
        final SSTRetrievalStrategy sstStrategy = new LoginSSTRetrievalStrategy(HttpClientBuilder.create().build(), jadlerHost, jadlerLogin, jadlerPassword);
        final HttpClient client = new GoodDataHttpClient(httpClient, sstStrategy);

        performGet(client, jadlerHost, GDC_PROJECTS_URL, HttpStatus.SC_OK);
    }

    @Test
    public void getProjectOkloginAndTtRefresh() throws IOException {
        onRequest()
                .havingMethodEqualTo("GET")
                .havingURIEqualTo(GDC_PROJECTS_URL)
                .havingHeaderEqualTo("Accept", "application/json")
        .respond()
                .withStatus(401)
                .withHeader("WWW-Authenticate", "GoodData realm=\"GoodData API\" cookie=GDCAuthTT")
                .withBody("<html><head><title>401 Authorization Required</title></head><body><p>This server could not verify that you are authorized to access the document requested.  Either you supplied the wrong credentials (e.g., bad password), or your browser doesn't understand how to supply the credentials required.Please see <a href=\"http://docs.gooddata.apiary.io/#login\">Authenticating to the GoodData API</a> for details.</p></body></html>")
                .withEncoding(Charset.forName("UTF-8"))
                .withContentType("application/json; charset=UTF-8")
        .thenRespond()
                .withStatus(200)
                .withBody("{\"about\":{\"summary\":\"Project Resources\",\"category\":\"Projects\",\"links\":[]}}")
                .withEncoding(Charset.forName("UTF-8"))
                .withContentType("application/json; charset=UTF-8");

        onRequest()
                .havingMethodEqualTo("GET")
                .havingURIEqualTo(GDC_TOKEN_URL)
        .respond()
                .withStatus(401)
                .withHeader("WWW-Authenticate", "GoodData realm=\"GoodData API\" cookie=GDCAuthSST")
                .withBody("{\"parameters\":[],\"component\":\"Account::Token\",\"message\":\"/gdc/account/login\"}")
                .withEncoding(Charset.forName("UTF-8"))
                .withContentType("application/json")
        .thenRespond()
                .withStatus(200)
                .withBody("{}")
                .withHeader("Set-Cookie", "GDCAuthTT=cookieTt; path=/gdc; secure; HttpOnly")
                .withEncoding(Charset.forName("UTF-8"))
                .withContentType("application/json");

        onRequest()
                .havingMethodEqualTo("POST")
                .havingURIEqualTo(GDC_LOGIN_URL)
                .havingHeaderEqualTo("Accept", "application/json; charset=UTF-8")
                .havingBodyEqualTo("{\"postUserLogin\":{\"login\":\"user@email.com\",\"password\":\"top secret\",\"remember\":0}}")
        .respond()
                .withStatus(200)
                .withBody("{\"userLogin\":{\"profile\":\"/gdc/account/profile/asdfasdf45t4ar\",\"state\":\"/gdc/account/login/asdfasdf45t4ar\"}}")
                .withContentType("application/json")
                .withEncoding(Charset.forName("UTF-8"))
                .withHeader("Set-Cookie", "GDCAuthSST=cookieSst; path=/gdc/account; secure; HttpOnly")
                .withHeader("Set-Cookie", "GDCAuthTT=; path=/gdc; expires=Sat, 18-May-2013 09:10:00 GMT; secure; HttpOnly");

        final HttpClient httpClient = HttpClientBuilder.create().build();
        final SSTRetrievalStrategy sstStrategy = new LoginSSTRetrievalStrategy(HttpClientBuilder.create().build(), jadlerHost, jadlerLogin, jadlerPassword);
        final HttpClient client = new GoodDataHttpClient(httpClient, sstStrategy);

        performGet(client, jadlerHost, GDC_PROJECTS_URL, HttpStatus.SC_OK);
    }

    @Test
    public void getProjectOkNoTtRefresh() throws IOException {
        onRequest()
                .havingMethodEqualTo("GET")
                .havingURIEqualTo(GDC_PROJECTS_URL)
                .havingHeaderEqualTo("Accept", "application/json")
        .respond()
                .withStatus(200)
                .withBody("{\"about\":{\"summary\":\"Project Resources\",\"category\":\"Projects\",\"links\":[]}}")
                .withEncoding(Charset.forName("UTF-8"))
                .withContentType("application/json; charset=UTF-8");
        final HttpClient httpClient = HttpClientBuilder.create().build();
        final SSTRetrievalStrategy sstStrategy = new LoginSSTRetrievalStrategy(HttpClientBuilder.create().build(), jadlerHost, jadlerLogin, jadlerPassword);
        final HttpClient client = new GoodDataHttpClient(httpClient, sstStrategy);

        performGet(client, jadlerHost, GDC_PROJECTS_URL, HttpStatus.SC_OK);
    }

        /**
         * For integration testing. Requires GoodData credentials.<br/>
         * Comment ignore annotation first and run
         * <code>mvn clean test -DGDC_LOGIN=user@email.com -DGDC_PASSWORD=password</code>
         */
    @Ignore
    @Test
    public void gdcLogin() throws IOException {
        final HttpClient httpClient = HttpClientBuilder.create().build();
        final SSTRetrievalStrategy sstStrategy = new LoginSSTRetrievalStrategy(HttpClientBuilder.create().build(), httpHost, login, password);
        final HttpClient client = new GoodDataHttpClient(httpClient, sstStrategy);

        performGet(client, httpHost, GDC_PROJECTS_URL, HttpStatus.SC_OK);
    }

    /**
     * For integration testing. Requires GoodData credentials.<br/>
     * Comment ignore annotation first and run
     * <code>mvn clean test -DGDC_SST=<put your SST here></></code>
     */
    @Ignore
    @Test
    public void gdcSstSimple() throws IOException {
        final HttpClient httpClient = HttpClientBuilder.create().build();
        final SSTRetrievalStrategy sstStrategy = new SimpleSSTRetrievalStrategy(sst);
        final HttpClient client = new GoodDataHttpClient(httpClient, sstStrategy);

        performGet(client, httpHost, GDC_PROJECTS_URL, HttpStatus.SC_OK);
    }
    
    private void performGet(HttpClient client, HttpHost httpHost, String url, int expectedStatus) throws IOException {
        HttpGet getProject = new HttpGet(url);
        getProject.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
        HttpResponse getProjectResponse = client.execute(httpHost, getProject);
        assertEquals(expectedStatus, getProjectResponse.getStatusLine().getStatusCode());
        EntityUtils.consume(getProjectResponse.getEntity());
    }

}
