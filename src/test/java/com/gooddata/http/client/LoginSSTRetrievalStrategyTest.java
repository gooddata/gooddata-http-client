/**
 * Copyright (C) 2007-2016, GoodData(R) Corporation. All rights reserved.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
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
import java.io.StringWriter;

import static com.gooddata.http.client.GoodDataHttpClient.SST_HEADER;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LoginSSTRetrievalStrategyTest {

    private static final String FAILURE_REASON = "Bad username or password";
    private static final String REQUEST_ID = "requestIdTest";
    private static final String PASSWORD = "mysecret";
    private static final String LOGIN = "user@server.com";
    private static final String SST = "xxxtopsecretSST";
    private static final String TT = "xxxtopsecretTT";

    private LoginSSTRetrievalStrategy sstStrategy;

    @Mock
    public HttpClient httpClient;

    @Mock
    public Logger logger;

    public StatusLine statusLine;

    private HttpHost host;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        host = new HttpHost("server.com", 123);
        sstStrategy = new LoginSSTRetrievalStrategy(LOGIN, PASSWORD);
    }

    @Test
    public void obtainSstHeader() throws IOException {
        statusLine = new BasicStatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_OK, "OK");
        final HttpResponse response = new BasicHttpResponse(statusLine);
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
        assertEquals("/gdc/account/login", postCaptor.getValue().getURI().getPath());
    }

    @Test(expected = GoodDataAuthException.class)
    public void obtainSst_badLogin() throws IOException {
        statusLine = new BasicStatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_BAD_REQUEST, "Bad Request");
        final HttpResponse response = new BasicHttpResponse(statusLine);
        when(httpClient.execute(any(HttpHost.class), any(HttpPost.class))).thenReturn(response);

        sstStrategy.obtainSst(httpClient, host);

    }

    @Test
    public void shouldLogout() throws Exception {
        statusLine = new BasicStatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_NO_CONTENT, "NO CONTENT");
        final HttpResponse response = new BasicHttpResponse(statusLine);
        when(httpClient.execute(isA(HttpHost.class), isA(HttpDelete.class))).thenReturn(response);

        sstStrategy.logout(httpClient, host, "/gdc/account/login/profileid", SST, TT);

        final ArgumentCaptor<HttpHost> hostCaptor = ArgumentCaptor.forClass(HttpHost.class);
        final ArgumentCaptor<HttpDelete> deleteCaptor = ArgumentCaptor.forClass(HttpDelete.class);

        verify(httpClient).execute(hostCaptor.capture(), deleteCaptor.capture());

        assertEquals("server.com", hostCaptor.getValue().getHostName());
        assertEquals(123, hostCaptor.getValue().getPort());

        final HttpDelete delete = deleteCaptor.getValue();
        assertNotNull(delete);
        assertEquals("/gdc/account/login/profileid", delete.getURI().getPath());
        assertEquals(SST, delete.getFirstHeader("X-GDC-AuthSST").getValue());
        assertEquals(TT, delete.getFirstHeader("X-GDC-AuthTT").getValue());
    }

    @Test
    public void shouldThrowOnLogoutError() throws Exception {
        statusLine = new BasicStatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_SERVICE_UNAVAILABLE, "downtime");
        final HttpResponse response = new BasicHttpResponse(statusLine);
        when(httpClient.execute(isA(HttpHost.class), isA(HttpDelete.class))).thenReturn(response);

        expectedException.expect(new GoodDataLogoutExceptionMatcher(503, "downtime"));

        sstStrategy.logout(httpClient, host, "/gdc/account/login/profileid", SST, TT);
    }

    @Test(expected = GoodDataAuthException.class)
    public void logLoginFailureRequestId() throws Exception{
        prepareLoginFailureResponse();
        try {
            sstStrategy.obtainSst(httpClient, host);
        } finally {
            ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);
            verify(logger).info(logMessageCaptor.capture());
            assertThat("Missing requestId at the log message",logMessageCaptor.getValue(), containsString(REQUEST_ID));
        }
    }

    @Test(expected = GoodDataAuthException.class)
    public void logLoginFailureReason() throws Exception{
        prepareLoginFailureResponse();
        try {
            sstStrategy.obtainSst(httpClient, host);
        } finally {
            ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);
            verify(logger).info(logMessageCaptor.capture());
            assertThat("Missing login failure at the log message",logMessageCaptor.getValue(), containsString(FAILURE_REASON));
        }
    }

    @Test(expected = GoodDataAuthException.class)
    public void logLoginFailureHttpStatus() throws Exception{
        prepareLoginFailureResponse();
        try {
            sstStrategy.obtainSst(httpClient, host);
        } finally {
            ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);
            verify(logger).info(logMessageCaptor.capture());
            assertThat("Missing HTTP response status at the log message",logMessageCaptor.getValue(), containsString("401"));
        }
    }

    private void prepareLoginFailureResponse() throws IOException, ClientProtocolException {
        statusLine = new BasicStatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_UNAUTHORIZED, "Unauthorized");
        final HttpResponse response = new BasicHttpResponse(statusLine);
        response.setHeader("X-GDC-Request", REQUEST_ID);
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(new ByteArrayInputStream(FAILURE_REASON.getBytes()));
        response.setEntity(entity);
        when(httpClient.execute(any(HttpHost.class), any(HttpPost.class))).thenReturn(response);
        sstStrategy.setLogger(logger);
    }
}
