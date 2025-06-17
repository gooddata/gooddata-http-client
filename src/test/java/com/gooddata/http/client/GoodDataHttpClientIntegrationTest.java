/*
 * (C) 2022 GoodData Corporation.
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jadler.Request;
import net.jadler.stubbing.RequestStubbing;
import net.jadler.stubbing.Responder;
import net.jadler.stubbing.ResponseStubbing;
import net.jadler.stubbing.StubResponse;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("squid:S2699")
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

    private static final String BODY_401 = "<html><head><title>401 Authorization Required</title></head><body><p>This server could not verify that you are authorized to access the document requested.  Either you supplied the wrong credentials (e.g., bad password), or your browser doesn't understand how to supply the credentials required.Please see <a href=\"https://help.gooddata.com/display/developer/API+Reference#/reference/authentication/log-in\">Authenticating to the GoodData API</a> for details.</p></body></html>";
    private static final String BODY_PROJECTS = "{\"about\":{\"summary\":\"Project Resources\",\"category\":\"Projects\",\"links\":[]}}";
    private static final String BODY_TOKEN_401 = "{\"parameters\":[],\"component\":\"Account::Token\",\"message\":\"/gdc/account/login\"}";

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_JSON_UTF = "application/json; charset=UTF-8";

    private static final Charset CHARSET = Charset.forName("UTF-8");

    private String jadlerLogin;
    private String jadlerPassword;
    private HttpHost jadlerHost;

    @BeforeEach
    public void setUp() {
        initJadler();

        jadlerLogin = "user@email.com";
        jadlerPassword = "top secret";
        jadlerHost = new HttpHost("http", "localhost", port());
    }

    @AfterEach
    public void tearDown() {
        closeJadler();
    }

    @Test
    public void vi () throws IOException {
        mock401OnProjects();
        mock401OnToken();

        requestOnLogin().respond()
                .withStatus(401)
                .withHeader(WWW_AUTHENTICATE_HEADER, GOODDATA_REALM)
                .withBody("{\"parameters\":[],\"component\":\"Account::Login::AuthShare\",\"message\":\"Bad Login or Password!\"}")
                .withContentType(CONTENT_TYPE_JSON);

        final GoodDataHttpClient client = createGoodDataClient(jadlerLogin, jadlerPassword, jadlerHost);

        performGet(client, jadlerHost, GDC_PROJECTS_PATH, HttpStatus.SC_UNAUTHORIZED);
    }


    @Test
    public void getProjectOkloginAndTtRefresh() throws Exception {

        onRequest()
        .havingMethodEqualTo("GET")
        .havingPathEqualTo(REDIRECT_PATH)
        .respond()
            .withStatus(200)
            .withBody(BODY_PROJECTS)
            .withEncoding(CHARSET)
            .withContentType(CONTENT_TYPE_JSON_UTF);

            
        mock401OnProjects();
        mock200OnProjects();

        mock401OnToken();
        mock200OnToken();

        mockLogin();

        final GoodDataHttpClient client = createGoodDataClient(jadlerLogin, jadlerPassword, jadlerHost);

        try {
            performGet(client, jadlerHost, REDIRECT_PATH, HttpStatus.SC_OK);
        } catch (IOException e) {
            throw new RuntimeException("GET request failed", e);
        }

    }




    private final class PerformGetWithCountDown implements Runnable {

        private final GoodDataHttpClient client;
        private final String path;
        private final CountDownLatch countDown;

        private PerformGetWithCountDown(GoodDataHttpClient client, String path, CountDownLatch countDown) {
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

        onRequest()
            .havingMethodEqualTo("GET")
            .havingPathEqualTo(GDC_PROJECTS_PATH)
            .respond()
            .withStatus(200)
            .withBody(BODY_PROJECTS)
            .withEncoding(CHARSET)
            .withContentType(CONTENT_TYPE_JSON_UTF);



        mock401OnToken();
        mock200OnToken();

        mockLogin();

        final GoodDataHttpClient client = createGoodDataClient(jadlerLogin, jadlerPassword, jadlerHost);

        try {
            performGet(client, jadlerHost, REDIRECT_PATH, HttpStatus.SC_OK);
        } catch (IOException e) {
            throw new RuntimeException("GET request failed", e);
        }

        
    }

    @Test
    public void getProjectOkNoTtRefresh() throws IOException {
        onRequest()
            .havingMethodEqualTo("GET")
            .havingPathEqualTo(REDIRECT_PATH)
            .respond()
                .withStatus(200)
                .withBody(BODY_PROJECTS)
                .withEncoding(CHARSET)
                .withContentType(CONTENT_TYPE_JSON_UTF);

        final GoodDataHttpClient client = createGoodDataClient(jadlerLogin, jadlerPassword, jadlerHost);

        try {
            performGet(client, jadlerHost, REDIRECT_PATH, HttpStatus.SC_OK);
        } catch (IOException e) {
            throw new RuntimeException("GET request failed", e);
        }
    }

    @Test
    public void shouldLogoutOk()  throws IOException {
        mock401OnProjects();
        mock200OnProjects();

        mock401OnToken();
        mock200OnToken();

        mockLogin();

        mockLogout("profileId");
    // Mock for the DELETE logout request
    // This ensures that a DELETE to /gdc/account/login/profileId returns HTTP 204 (No Content)
        onRequest()
            .havingMethodEqualTo("DELETE")
            .havingPathEqualTo("/gdc/account/login/profileId")
            .respond()
            .withStatus(204);
    // Mock for the GET request after redirect
    // This ensures GET on REDIRECT_PATH returns 200 with a valid JSON body
        onRequest()
            .havingMethodEqualTo("GET")
            .havingPathEqualTo(REDIRECT_PATH)
            .respond()
            .withStatus(200)
            .withBody(BODY_PROJECTS)
            .withEncoding(CHARSET)
            .withContentType(CONTENT_TYPE_JSON_UTF);

        final GoodDataHttpClient client = createGoodDataClient(jadlerLogin, jadlerPassword, jadlerHost);

        try {
            // Perform the GET request and expect HTTP 200 OK
            performGet(client, jadlerHost, REDIRECT_PATH, HttpStatus.SC_OK);
        } catch (IOException e) {
            throw new RuntimeException("GET request failed", e);
        }

    // Test the logout operation and expect HTTP 204 No Content
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
        onRequest()
            .havingMethodEqualTo("GET")
            .havingPathEqualTo(url)
            .havingHeaderEqualTo(TT_HEADER, tt)
            .respondUsing(request -> {
                return StubResponse.builder()
                    .status(200)
                    .body(BODY_PROJECTS, CHARSET)
                    .header(CONTENT_HEADER, CONTENT_TYPE_JSON_UTF)
                    .build();
            });
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
                .withHeader(TT_HEADER, tt)
                .withEncoding(CHARSET)
                ;
    }

    private static void mockLogin() {
        requestOnLogin()
                .respond()
                .withStatus(200)
                .withHeader(SST_HEADER, "SST")
                .withEncoding(CHARSET);
    }


    private static RequestStubbing requestOnLogin() {
        return onRequest()
                .havingMethodEqualTo("POST")
                .havingPathEqualTo(GDC_LOGIN_PATH);
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
