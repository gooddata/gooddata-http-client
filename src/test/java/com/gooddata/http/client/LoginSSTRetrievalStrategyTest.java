/*
 * Copyright (C) 2007-2013, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.SM;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LoginSSTRetrievalStrategyTest {

    public static final String PASSWORD = "mysecret";
    public static final String LOGIN = "user@server.com";

    private LoginSSTRetrievalStrategy sstStrategy;

    @Mock
    public HttpClient httpClient;

    public StatusLine statusLine;

    private HttpHost host;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        host = new HttpHost("server.com", 123);
        sstStrategy = new LoginSSTRetrievalStrategy(httpClient, host, LOGIN, PASSWORD);
    }

    @Test
    public void obtainSst() throws IOException {
        statusLine = new BasicStatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_OK, "OK");
        final HttpResponse response = new BasicHttpResponse(statusLine);
        response.setHeader(SM.SET_COOKIE, "GDCAuthSST=xxxtopsecretcookieSST; path=/gdc/account; secure; HttpOnly");
        when(httpClient.execute(isA(HttpHost.class), isA(HttpPost.class))).thenReturn(response);

        assertEquals("xxxtopsecretcookieSST", sstStrategy.obtainSst());

        final ArgumentCaptor<HttpHost> hostCaptor = ArgumentCaptor.forClass(HttpHost.class);
        final ArgumentCaptor<HttpPost> postCaptor = ArgumentCaptor.forClass(HttpPost.class);

        verify(httpClient).execute(hostCaptor.capture(), postCaptor.capture());

        assertEquals("server.com", hostCaptor.getValue().getHostName());
        assertEquals(123, hostCaptor.getValue().getPort());

        final String postBody = "{\"postUserLogin\":{\"login\":\"" + LOGIN + "\",\"password\":\"" + PASSWORD + "\",\"remember\":0}}";//TODO: JSON assert
        StringWriter writer = new StringWriter();
        IOUtils.copy(postCaptor.getValue().getEntity().getContent(), writer, "UTF-8");;

        assertEquals(postBody, writer.toString());
        assertEquals("/gdc/account/login", postCaptor.getValue().getURI().getPath());
    }

    @Test(expected = GoodDataAuthException.class)
    public void obtainSst_badLogin() throws IOException {
        statusLine = new BasicStatusLine(new ProtocolVersion("https", 1, 1), HttpStatus.SC_BAD_REQUEST, "Bad Request");
        final HttpResponse response = new BasicHttpResponse(statusLine);
        when(httpClient.execute(any(HttpHost.class), any(HttpPost.class))).thenReturn(response);

        sstStrategy.obtainSst();

    }
}
