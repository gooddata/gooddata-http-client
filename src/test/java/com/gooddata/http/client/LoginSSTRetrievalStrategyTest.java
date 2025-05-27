/*
 * (C) 2022 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.apache.commons.io.IOUtils;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;

import static com.gooddata.http.client.GoodDataHttpClient.SST_HEADER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
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
    private AutoCloseable mockClass;

    @Mock
    public HttpClient httpClient;

    @Mock
    public Logger logger;

    public StatusLine statusLine;

    private HttpHost host;

    @BeforeEach
    public void setUp() {
        mockClass = MockitoAnnotations.openMocks(this);
        host = new HttpHost("server.com", 123);
        sstStrategy = new LoginSSTRetrievalStrategy(LOGIN, PASSWORD);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mockClass.close();
    }


    @Test
    public void obtainSstHeader() throws IOException {
        statusLine = new StatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_OK, "OK");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setHeader(SST_HEADER, SST);
        when(httpClient.execute(
                isA(HttpHost.class), isA(HttpPost.class), isA(org.apache.hc.core5.http.io.HttpClientResponseHandler.class)))
            .thenAnswer(invocation -> {
                org.apache.hc.core5.http.io.HttpClientResponseHandler<?> handler = invocation.getArgument(2);
                return handler.handleResponse(response);
            });


        assertEquals(SST, sstStrategy.obtainSst(httpClient, host));

        final ArgumentCaptor<HttpHost> hostCaptor = ArgumentCaptor.forClass(HttpHost.class);
        final ArgumentCaptor<HttpPost> postCaptor = ArgumentCaptor.forClass(HttpPost.class);

        verify(httpClient).execute(hostCaptor.capture(), postCaptor.capture(), any(org.apache.hc.core5.http.io.HttpClientResponseHandler.class));

        assertEquals("server.com", hostCaptor.getValue().getHostName());
        assertEquals(123, hostCaptor.getValue().getPort());

        final String postBody = "{\"postUserLogin\":{\"login\":\"" + LOGIN + "\",\"password\":\"" + PASSWORD + "\",\"remember\":0,\"verify_level\":2}}";//TODO: JSON assert
        StringWriter writer = new StringWriter();
        IOUtils.copy(postCaptor.getValue().getEntity().getContent(), writer, "UTF-8");

        assertEquals(postBody, writer.toString());
        try {
            assertEquals("/gdc/account/login", postCaptor.getValue().getUri().getPath());
        } catch (URISyntaxException e) {
            fail("Invalid URI: " + e.getMessage());
        }
    }

    @Test
    public void obtainSst_badLogin() throws IOException {
        statusLine = new StatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_BAD_REQUEST, "Bad Request");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_BAD_REQUEST, "Bad Request");

    when(httpClient.executeOpen(
        any(HttpHost.class),
        any(HttpPost.class),
        isNull(HttpContext.class)
    )).thenReturn(response);

        assertThrows(GoodDataAuthException.class, () -> sstStrategy.obtainSst(httpClient, host));
    }

    @Test
    public void shouldLogout() throws Exception {
        statusLine = new StatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_NO_CONTENT, "NO CONTENT");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "NO CONTENT");
        when(httpClient.executeOpen(
            isA(HttpHost.class),
            isA(HttpDelete.class),
            isNull(HttpContext.class)
        )).thenReturn(response);

        sstStrategy.logout(httpClient, host, "/gdc/account/login/profileid", SST, TT);

        final ArgumentCaptor<HttpHost> hostCaptor = ArgumentCaptor.forClass(HttpHost.class);
        final ArgumentCaptor<HttpDelete> deleteCaptor = ArgumentCaptor.forClass(HttpDelete.class);

        verify(httpClient).executeOpen(
            hostCaptor.capture(),
            deleteCaptor.capture(),
            isNull(HttpContext.class)
        );

        assertEquals("server.com", hostCaptor.getValue().getHostName());
        assertEquals(123, hostCaptor.getValue().getPort());

        final HttpDelete delete = deleteCaptor.getValue();
        assertNotNull(delete);
        assertEquals("/gdc/account/login/profileid", delete.getUri().getPath());    
        assertEquals(SST, delete.getFirstHeader("X-GDC-AuthSST").getValue());
        assertEquals(TT, delete.getFirstHeader("X-GDC-AuthTT").getValue());
    }

    @Test
    public void shouldThrowOnLogoutError() throws Exception {
        statusLine = new StatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_SERVICE_UNAVAILABLE, "downtime");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "downtime");
        when(httpClient.execute(isA(HttpHost.class), isA(HttpDelete.class))).thenReturn(response);

        assertThrows(GoodDataLogoutException.class, () -> 
            sstStrategy.logout(httpClient, host, "/gdc/account/login/profileid", SST, TT));
    }

    @Test
    public void logLoginFailureRequestId() throws Exception{
        prepareLoginFailureResponse();
        Exception ex = assertThrows(GoodDataAuthException.class, () -> sstStrategy.obtainSst(httpClient, host));
        ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger).info(logMessageCaptor.capture());
        assertThat("Missing requestId at the log message", logMessageCaptor.getValue(), containsString(REQUEST_ID));
    }

    @Test
    public void logLoginFailureReason() throws Exception{
        prepareLoginFailureResponse();
        Exception ex = assertThrows(GoodDataAuthException.class, () -> sstStrategy.obtainSst(httpClient, host));
        ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger).info(logMessageCaptor.capture());
        assertThat("Missing login failure at the log message", logMessageCaptor.getValue(), containsString(FAILURE_REASON));
    }

    @Test
    public void logLoginFailureHttpStatus() throws Exception{
        prepareLoginFailureResponse();
        Exception ex = assertThrows(GoodDataAuthException.class, () -> sstStrategy.obtainSst(httpClient, host));
        ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger).info(logMessageCaptor.capture());
        assertThat("Missing HTTP response status at the log message", logMessageCaptor.getValue(), containsString("401"));
    }

private void prepareLoginFailureResponse() throws IOException {
    statusLine = new StatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_UNAUTHORIZED, "Unauthorized");


    CloseableHttpResponse response = mock(CloseableHttpResponse.class);
    when(response.getCode()).thenReturn(HttpStatus.SC_UNAUTHORIZED);
    when(response.getReasonPhrase()).thenReturn("Unauthorized");
    when(response.getFirstHeader("X-GDC-Request")).thenReturn(new BasicHeader("X-GDC-Request", REQUEST_ID));

    StringEntity entity = new StringEntity(FAILURE_REASON, ContentType.TEXT_PLAIN);
    when(response.getEntity()).thenReturn(entity);

    when(httpClient.execute(any(HttpHost.class), any(HttpPost.class))).thenReturn(response);
    when(httpClient.execute(any(HttpHost.class), any(HttpPost.class), any(HttpContext.class))).thenReturn(response);

    sstStrategy.setLogger(logger);
}
}
