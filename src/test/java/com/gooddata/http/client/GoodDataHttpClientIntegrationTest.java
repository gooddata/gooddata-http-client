/**
 * Copyright (C) 2007-2016, GoodData(R) Corporation. All rights reserved.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;


import static com.gooddata.http.client.TestUtils.createGoodDataClient;
import static com.gooddata.http.client.TestUtils.logout;
import static com.gooddata.http.client.TestUtils.performGet;
import static net.jadler.Jadler.closeJadler;
import static net.jadler.Jadler.initJadler;
import static net.jadler.Jadler.onRequest;
import static net.jadler.Jadler.port;
import static net.jadler.Jadler.verifyThatRequest;

import net.jadler.Request;
import net.jadler.stubbing.RequestStubbing;
import net.jadler.stubbing.Responder;
import net.jadler.stubbing.ResponseStubbing;
import net.jadler.stubbing.StubResponse;

import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class GoodDataHttpClientIntegrationTest {

    private static final String GDC_TOKEN_PATH = "/gdc/account/token";
    private static final String GDC_LOGIN_PATH = "/gdc/account/login";
    private static final String GDC_PROJECTS_PATH = "/gdc/projects";
    private static final String GDC_PROJECTS2_PATH = "/gdc/projects2";
    private static final String REDIRECT_PATH = "/redirect/to/projects";

    private static final String SST_HEADER = "X-GDC-AuthSST";
    private static final String WWW_AUTHENTICATE_HEADER = "WWW-Authenticate";
    private static final String ACCEPT_HEADER = "Accept";
    private static final String CONTENT_HEADER = "Content-Type";
    private static final String TT_HEADER = "X-GDC-AuthTT";

    private static final String GOODDATA_REALM = "GoodData realm=\"GoodData API\"";
    private static final String TT_COOKIE = "cookie=GDCAuthTT";

    private static final String BODY_401 = "<html><head><title>401 Authorization Required</title></head><body><p>This server could not verify that you are authorized to access the document requested.  Either you supplied the wrong credentials (e.g., bad password), or your browser doesn't understand how to supply the credentials required.Please see <a href=\"http://docs.gooddata.apiary.io/#login\">Authenticating to the GoodData API</a> for details.</p></body></html>";
    private static final String BODY_PROJECTS = "{\"about\":{\"summary\":\"Project Resources\",\"category\":\"Projects\",\"links\":[]}}";
    private static final String BODY_TOKEN_401 = "{\"parameters\":[],\"component\":\"Account::Token\",\"message\":\"/gdc/account/login\"}";

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_JSON_UTF = "application/json; charset=UTF-8";
    private static final String CONTENT_TYPE_YAML = "application/yaml";

    private static final Charset CHARSET = Charset.forName("UTF-8");

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

    @Test
    public void getProjectsBadLogin() throws IOException {
        mock401OnProjects();
        mock401OnToken();

        requestOnLogin().respond()
                .withStatus(401)
                .withHeader(WWW_AUTHENTICATE_HEADER, GOODDATA_REALM)
                .withBody("{\"parameters\":[],\"component\":\"Account::Login::AuthShare\",\"message\":\"Bad Login or Password!\"}")
                .withContentType(CONTENT_TYPE_JSON);

        final HttpClient client = createGoodDataClient(jadlerLogin, jadlerPassword, jadlerHost);

        performGet(client, jadlerHost, GDC_PROJECTS_PATH, HttpStatus.SC_UNAUTHORIZED);
    }

    @Test
    public void getProjectOkloginAndTtRefresh() throws Exception {
        mock401OnProjects();
        mock200OnProjects();

        mock401OnToken();
        mock200OnToken();

        mockLogin();

        final HttpClient client = createGoodDataClient(jadlerLogin, jadlerPassword, jadlerHost);

        performGet(client, jadlerHost, GDC_PROJECTS_PATH, HttpStatus.SC_OK);
    }

    @Test
    public void shouldRefreshTTConcurrent() throws Exception {
        mock401OnProjects();

        // this serves to block second thread, until the first one gets 401 on projects, which causes TT refresh
        // the test aims to test the second thread is not cycling on 401 and cumulating wrong TT headers
        final Semaphore semaphore = new Semaphore(1);
        
        requestOnPath(GDC_PROJECTS_PATH, "TT1")
        .respondUsing(new Responder() {
            boolean first = true;

            @Override
            public StubResponse nextResponse(Request request) {
                if (first) {
                    first = false;
                    return StubResponse.builder()
                            .status(200)
                            .body(BODY_PROJECTS, CHARSET)
                            .header(CONTENT_HEADER, CONTENT_TYPE_JSON_UTF)
                            .build();
                } else {
                    semaphore.release();
                    return StubResponse.builder()
                            .status(401)
                            .body(BODY_401, CHARSET)
                            .header(CONTENT_HEADER, CONTENT_TYPE_JSON_UTF)
                            .header(WWW_AUTHENTICATE_HEADER, GOODDATA_REALM + " " + TT_COOKIE)
                            .delay(5, TimeUnit.SECONDS)
                            .build();
                }
            }
        });
        mock200OnProjects("TT2");
        
        mock401OnPath(GDC_PROJECTS2_PATH, "TT1");
        mock200OnPath(GDC_PROJECTS2_PATH, "TT2");

        mock401OnToken();
        respond200OnToken(
                mock200OnToken("TT1").thenRespond(),
                "TT2");

        mockLogin();

        final HttpClient client = createGoodDataClient(jadlerLogin, jadlerPassword, jadlerHost);

        // one get at the beginning causing successful login
        performGet(client, jadlerHost, GDC_PROJECTS_PATH, 200);

        // to be able to finish when both threads finished
        final CountDownLatch countDown = new CountDownLatch(2);

        final ExecutorService executor = Executors.newFixedThreadPool(2);
        semaphore.acquire(); // will be released in jadler
        executor.submit(new PerformGetWithCountDown(client, GDC_PROJECTS_PATH, countDown));
        semaphore.acquire(); // causes waiting
        executor.submit(new PerformGetWithCountDown(client, GDC_PROJECTS2_PATH, countDown));

        countDown.await(10, TimeUnit.SECONDS);

        verifyThatRequest()
                .havingMethodEqualTo("GET")
                .havingPathEqualTo(GDC_TOKEN_PATH)
                .havingHeaderEqualTo(SST_HEADER, "SST")
                // if received more than twice, it means the second thread didn't wait, while the first was refreshing TT
                .receivedTimes(2);

        verifyThatRequest()
                .havingMethodEqualTo("GET")
                .havingPathEqualTo(GDC_PROJECTS2_PATH)
                .havingHeaderEqualTo(TT_HEADER, "TT1")
                // the second thread should try only once with expired TT1
                .receivedOnce();

        verifyThatRequest()
                .havingMethodEqualTo("GET")
                .havingPathEqualTo(GDC_PROJECTS2_PATH)
                .havingHeaderEqualTo(TT_HEADER, "TT1")
                .havingHeaderEqualTo(TT_HEADER, "TT2")
                // the second thread should not set more than one X-GDC-AuthTT header
                .receivedNever();
    }

    private final class PerformGetWithCountDown implements Runnable {

        private final HttpClient client;
        private final String path;
        private final CountDownLatch countDown;

        private PerformGetWithCountDown(HttpClient client, String path, CountDownLatch countDown) {
            this.client = client;
            this.path = path;
            this.countDown = countDown;
        }

        @Override
        public void run() {
            try {
                performGet(client, jadlerHost, path, HttpStatus.SC_OK);
            } catch (IOException e) {
                throw new IllegalStateException("Can't execute get", e);
            } finally {
                countDown.countDown();
            }
        }
    }

    @Test
    public void redirect() throws IOException {
        onRequest()
                .havingMethodEqualTo("GET")
                .havingPathEqualTo(REDIRECT_PATH)
        .respond()
                .withStatus(302)
                .withHeader("Location", GDC_PROJECTS_PATH);

        mock401OnProjects();

        mock200OnProjects();

        mock401OnToken();
        mock200OnToken();

        mockLogin();

        final HttpClient client = createGoodDataClient(jadlerLogin, jadlerPassword, jadlerHost);

        performGet(client, jadlerHost, REDIRECT_PATH, HttpStatus.SC_OK);
    }

    @Test
    public void getProjectOkNoTtRefresh() throws IOException {
        mock200OnProjects(null); // null to simplify the boilerplate
        final HttpClient client = createGoodDataClient(jadlerLogin, jadlerPassword, jadlerHost);

        performGet(client, jadlerHost, GDC_PROJECTS_PATH, HttpStatus.SC_OK);
    }

    @Test
    public void shouldLogoutOk()  throws IOException {
        mock401OnProjects();
        mock200OnProjects();

        mock401OnToken();
        mock200OnToken();

        mockLogin();

        mockLogout("profileId");

        final HttpClient client = createGoodDataClient(jadlerLogin, jadlerPassword, jadlerHost);

        performGet(client, jadlerHost, GDC_PROJECTS_PATH, HttpStatus.SC_OK);

        logout(client, jadlerHost, "profileId", HttpStatus.SC_NO_CONTENT);
    }

    private static void mock401OnProjects() {
        mock401OnPath(GDC_PROJECTS_PATH, null);
    }

    private static void mock401OnPath(String url, String tt) {
        requestOnPath(url, tt)
            .respond()
                .withStatus(401)
                .withHeader(WWW_AUTHENTICATE_HEADER, GOODDATA_REALM + " " + TT_COOKIE)
                .withBody(BODY_401)
                .withEncoding(CHARSET)
                .withContentType(CONTENT_TYPE_JSON_UTF);
    }

    private static RequestStubbing requestOnPath(String url, String tt) {
        final RequestStubbing requestStubbing = onRequest()
                .havingMethodEqualTo("GET")
                .havingPathEqualTo(url)
                .havingHeaderEqualTo(ACCEPT_HEADER, CONTENT_TYPE_JSON);
        return  tt != null 
                ? requestStubbing.havingHeaderEqualTo(TT_HEADER, tt)
                : requestStubbing;
    }

    private static void mock200OnProjects() {
        mock200OnProjects("TT");
    }

    private static void mock200OnProjects(String tt) {
        mock200OnPath(GDC_PROJECTS_PATH, tt);
    }

    private static void mock200OnPath(String url, String tt) {
       requestOnPath(url, tt)
            .respond()
                .withStatus(200)
                .withBody(BODY_PROJECTS)
                .withEncoding(CHARSET)
                .withContentType(CONTENT_TYPE_JSON_UTF);
    }

    private static void mock401OnToken() {
        onRequest()
                .havingMethodEqualTo("GET")
                .havingPathEqualTo(GDC_TOKEN_PATH)
        .respond()
                .withStatus(401)
                .withHeader(WWW_AUTHENTICATE_HEADER, GOODDATA_REALM + " cookie=GDCAuthSST")
                .withBody(BODY_TOKEN_401)
                .withEncoding(CHARSET)
                .withContentType(CONTENT_TYPE_JSON);
    }

    private static void mock200OnToken() {
        mock200OnToken("TT");
    }

    private static ResponseStubbing mock200OnToken(String tt) {
        return respond200OnToken(
                onRequest()
                        .havingMethodEqualTo("GET")
                        .havingPathEqualTo(GDC_TOKEN_PATH)
                        .havingHeaderEqualTo(SST_HEADER, "SST")
                        .respond(), tt);
    }

    private static ResponseStubbing respond200OnToken(ResponseStubbing stub, String tt) {
        return stub
                .withStatus(200)
                .withBody("---\n  userToken\n    token: " + tt)
                .withEncoding(CHARSET)
                .withContentType(CONTENT_TYPE_JSON);
    }

    private static void mockLogin() {
        requestOnLogin()
                .respond()
                .withStatus(200)
                .withBody("---\nuserLogin\n  profile: /gdc/account/profile/asdfasdf45t4ar\n  token: SST\n  state: /gdc/account/login/asdfasdf45t4ar")
                .withContentType(CONTENT_TYPE_YAML)
                .withEncoding(CHARSET);
    }


    private static RequestStubbing requestOnLogin() {
        return onRequest()
                .havingMethodEqualTo("POST")
                .havingPathEqualTo(GDC_LOGIN_PATH)
                .havingHeaderEqualTo(ACCEPT_HEADER, CONTENT_TYPE_YAML)
                .havingBodyEqualTo("{\"postUserLogin\":{\"login\":\"user@email.com\",\"password\":\"top secret\",\"remember\":0,\"verify_level\":2}}");
    }

    private static void mockLogout(String profileId) {
        onRequest()
                .havingMethodEqualTo("DELETE")
                .havingPathEqualTo(GDC_LOGIN_PATH + "/" + profileId)
                .havingHeaderEqualTo(SST_HEADER, "SST")
                .havingHeaderEqualTo(TT_HEADER, "TT")
            .respond()
                .withStatus(204);
    }





}
