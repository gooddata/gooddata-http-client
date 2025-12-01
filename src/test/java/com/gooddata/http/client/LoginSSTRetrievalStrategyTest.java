/*
 * (C) 2025 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.StatusLine;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import static com.gooddata.http.client.GoodDataHttpClient.SST_HEADER;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LoginSSTRetrievalStrategyTest {

    private static final String FAILURE_REASON = "Bad username or password";
    private static final String REQUEST_ID = "requestIdTest";
    private static final String PASSWORD = "mysecret";
    private static final String LOGIN = "user@server.com";
    private static final String SST = "xxxtopsecretSST";
    private static final String TT = "xxxtopsecretTT";
    @Mock
    public HttpClient httpClient;
    @Mock
    public Logger logger;
    public StatusLine statusLine;
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private LoginSSTRetrievalStrategy sstStrategy;
    private AutoCloseable mockClass;
    private HttpHost host;

    @Before
    public void setUp() {
        mockClass = MockitoAnnotations.openMocks(this);
        host = new HttpHost("server.com", 123);
        sstStrategy = new LoginSSTRetrievalStrategy(LOGIN, PASSWORD);
    }

    @After
    public void tearDown() throws Exception {
        mockClass.close();
    }

    @Test
    public void obtainSstHeader() throws IOException {
        statusLine = new StatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_OK, "OK");
        final BasicClassicHttpResponse response = new BasicClassicHttpResponse(statusLine.getStatusCode(), statusLine.getReasonPhrase());
        response.setHeader(SST_HEADER, SST);
        when(httpClient.execute(isA(HttpHost.class), isA(HttpPost.class))).thenReturn(response);

        assertEquals(SST, sstStrategy.obtainSst(httpClient, host));

        final ArgumentCaptor<HttpHost> hostCaptor = ArgumentCaptor.forClass(HttpHost.class);
        final ArgumentCaptor<HttpPost> postCaptor = ArgumentCaptor.forClass(HttpPost.class);

        verify(httpClient).execute(hostCaptor.capture(), postCaptor.capture());

        assertEquals("server.com", hostCaptor.getValue().getHostName());
        assertEquals(123, hostCaptor.getValue().getPort());

        final String postBody = "{\"postUserLogin\":{\"login\":\"" + LOGIN + "\",\"password\":\"" + PASSWORD + "\",\"remember\":0,\"verify_level\":2}}";//TODO: JSON assert
        StringWriter writer = new StringWriter();
        IOUtils.copy(postCaptor.getValue().getEntity().getContent(), writer, "UTF-8");

        assertEquals(postBody, writer.toString());
        assertEquals("/gdc/account/login", postCaptor.getValue().getPath());
    }

    @Test(expected = GoodDataAuthException.class)
    public void obtainSst_badLogin() throws IOException {
        statusLine = new StatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_BAD_REQUEST, "Bad Request");
        final BasicClassicHttpResponse response = new BasicClassicHttpResponse(statusLine.getStatusCode(), statusLine.getReasonPhrase());
        when(httpClient.execute(any(HttpHost.class), any(HttpPost.class))).thenReturn(response);

        sstStrategy.obtainSst(httpClient, host);

    }

    @Test
    public void shouldLogout() throws Exception {
        statusLine = new StatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_NO_CONTENT, "NO CONTENT");
        final BasicClassicHttpResponse response = new BasicClassicHttpResponse(statusLine.getStatusCode(), statusLine.getReasonPhrase());
        when(httpClient.execute(isA(HttpHost.class), isA(HttpDelete.class))).thenReturn(response);

        sstStrategy.logout(httpClient, host, "/gdc/account/login/profileid", SST, TT);

        final ArgumentCaptor<HttpHost> hostCaptor = ArgumentCaptor.forClass(HttpHost.class);
        final ArgumentCaptor<HttpDelete> deleteCaptor = ArgumentCaptor.forClass(HttpDelete.class);

        verify(httpClient).execute(hostCaptor.capture(), deleteCaptor.capture());

        assertEquals("server.com", hostCaptor.getValue().getHostName());
        assertEquals(123, hostCaptor.getValue().getPort());

        final HttpDelete delete = deleteCaptor.getValue();
        assertNotNull(delete);
        assertEquals("/gdc/account/login/profileid", delete.getPath());
        assertEquals(SST, delete.getFirstHeader("X-GDC-AuthSST").getValue());
        assertEquals(TT, delete.getFirstHeader("X-GDC-AuthTT").getValue());
    }

    @Test
    public void shouldThrowOnLogoutError() throws Exception {
        statusLine = new StatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_SERVICE_UNAVAILABLE, "downtime");
        final BasicClassicHttpResponse response = new BasicClassicHttpResponse(statusLine.getStatusCode(), statusLine.getReasonPhrase());
        when(httpClient.execute(isA(HttpHost.class), isA(HttpDelete.class))).thenReturn(response);

        expectedException.expect(new GoodDataLogoutExceptionMatcher(503, "downtime"));

        sstStrategy.logout(httpClient, host, "/gdc/account/login/profileid", SST, TT);
    }

    @Test(expected = GoodDataAuthException.class)
    public void logLoginFailureRequestId() throws Exception {
        prepareLoginFailureResponse();
        try {
            sstStrategy.obtainSst(httpClient, host);
        } finally {
            ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);
            verify(logger).info(logMessageCaptor.capture());
            assertThat("Missing requestId at the log message", logMessageCaptor.getValue(), containsString(REQUEST_ID));
        }
    }

    @Test(expected = GoodDataAuthException.class)
    public void logLoginFailureReason() throws Exception {
        prepareLoginFailureResponse();
        try {
            sstStrategy.obtainSst(httpClient, host);
        } finally {
            ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);
            verify(logger).info(logMessageCaptor.capture());
            assertThat("Missing login failure at the log message", logMessageCaptor.getValue(), containsString(FAILURE_REASON));
        }
    }

    @Test(expected = GoodDataAuthException.class)
    public void logLoginFailureHttpStatus() throws Exception {
        prepareLoginFailureResponse();
        try {
            sstStrategy.obtainSst(httpClient, host);
        } finally {
            ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);
            verify(logger).info(logMessageCaptor.capture());
            assertThat("Missing HTTP response status at the log message", logMessageCaptor.getValue(), containsString("401"));
        }
    }

    private void prepareLoginFailureResponse() throws IOException {
        statusLine = new StatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_UNAUTHORIZED, "Unauthorized");
        final BasicClassicHttpResponse response = new BasicClassicHttpResponse(statusLine.getStatusCode(), statusLine.getReasonPhrase());
        response.setHeader("X-GDC-Request", REQUEST_ID);
        InputStream content = new ByteArrayInputStream(FAILURE_REASON.getBytes());
        int contentLength = FAILURE_REASON.getBytes().length;
        HttpEntity entity = new BasicHttpEntity(content, contentLength, ContentType.TEXT_PLAIN);
        response.setEntity(entity);
        when(httpClient.execute(any(HttpHost.class), any(HttpPost.class))).thenReturn(response);
        sstStrategy.setLogger(logger);
    }
}
