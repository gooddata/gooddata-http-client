/*
 * Copyright (C) 2007-2013, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class GoodDataHttpClientTest {

    private GoodDataHttpClient goodDataHttpClient;

    @Mock
    public HttpClient httpClient;

    @Mock
    public SSTRetrievalStrategy sstStrategy;

    private HttpResponse ttChallengeResponse;

    private HttpResponse sstChallengeResponse;

    private HttpResponse okResponse;

    private HttpResponse ttRefreshedResponse;

    private HttpResponse response401;

    private HttpHost host;

    private HttpGet get;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        host = new HttpHost("server.com");
        get = new HttpGet("/url");
        goodDataHttpClient = new GoodDataHttpClient(httpClient, sstStrategy);

        ttChallengeResponse = createResponse(HttpStatus.SC_UNAUTHORIZED, "<html><head><title>401 Authorization Required</title></head><body></body>", "Unauthorized");
        ttChallengeResponse.setHeader(new BasicHeader("WWW-Authenticate", "GoodData realm=\"GoodData API\" cookie=GDCAuthTT"));

        sstChallengeResponse = createResponse(HttpStatus.SC_UNAUTHORIZED, "<html><head><title>401 Authorization Required</title></head><body></body>", "Unauthorized");
        sstChallengeResponse.setHeader(new BasicHeader("WWW-Authenticate", "GoodData realm=\"GoodData API\" cookie=GDCAuthSST"));

        response401 = createResponse(HttpStatus.SC_UNAUTHORIZED, "<html><head><title>401 Authorization Required</title></head><body></body>", "Unauthorized");

        okResponse = createResponse(HttpStatus.SC_OK, "<html><head><title>OK</title></head><body></body>", "OK");

        ttRefreshedResponse = createResponse(HttpStatus.SC_OK, "<html><head><title>TT OK</title></head><body></body>", "OK");
    }

    private HttpResponse createResponse(int status, String body, String reasonPhrase) {
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion("https", 1, 1), status, reasonPhrase));
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(new ByteArrayInputStream(body.getBytes()));
        response.setEntity(entity);
        return response;
    }

    @Test
    public void execute_sstExpired() throws IOException {
        when(httpClient.execute(eq(host), any(HttpRequest.class), any(HttpContext.class))) // original requests
                .thenReturn(ttChallengeResponse)
                .thenReturn(sstChallengeResponse)
                .thenReturn(ttRefreshedResponse)
                .thenReturn(okResponse);

        assertEquals(okResponse, goodDataHttpClient.execute(host, get));

        verify(sstStrategy, only()).obtainSst();
        verify(httpClient, times(2)).execute(eq(host), eq(get), any(HttpContext.class));
        verify(httpClient, times(4)).execute(eq(host), any(HttpRequest.class), any(HttpContext.class));
    }

    @Test(expected = GoodDataAuthException.class)
    public void execute_unableObtainSst() throws IOException {
        when(httpClient.execute(eq(host), any(HttpRequest.class), any(HttpContext.class)))
                .thenReturn(ttChallengeResponse)
                .thenReturn(response401);

        goodDataHttpClient.execute(host, get);

        verify(sstStrategy, only()).obtainSst();
        verify(httpClient, only()).execute(eq(host), eq(get), any(HttpContext.class));
        verify(httpClient, only()).execute(eq(host), any(HttpRequest.class));
    }

    @Test(expected = GoodDataAuthException.class)
    public void execute_unableObtainTTafterSuccessfullSstObtained() throws IOException {
        when(httpClient.execute(eq(host), any(HttpRequest.class), any(HttpContext.class)))
                .thenReturn(ttChallengeResponse)
                .thenReturn(sstChallengeResponse)
                .thenReturn(response401);

        goodDataHttpClient.execute(host, get);
    }

    @Test
    public void execute_nonChallenge401() throws IOException {
        when(httpClient.execute(eq(host), eq(get), any(HttpContext.class)))
                .thenReturn(response401);

        assertEquals(response401, goodDataHttpClient.execute(host, get));

        verify(sstStrategy, never()).obtainSst();
        verify(httpClient, only()).execute(eq(host), eq(get), any(HttpContext.class));
    }

    /**
     * No TT or SST refresh needed.
     * @throws IOException
     */
    @Test
    public void execute_okResponse() throws IOException {
        when(httpClient.execute(eq(host), eq(get), any(HttpContext.class)))
                .thenReturn(okResponse);

        assertEquals(okResponse, goodDataHttpClient.execute(host, get));

        verify(sstStrategy, never()).obtainSst();
        verify(httpClient, only()).execute(eq(host), eq(get), any(HttpContext.class));
    }

    @Test
    public void execute_ttRefreshOnly() throws IOException {
        when(httpClient.execute(eq(host), any(HttpRequest.class), any(HttpContext.class)))
                .thenReturn(ttChallengeResponse)
                .thenReturn(ttRefreshedResponse)
                .thenReturn(okResponse);


        assertEquals(okResponse, goodDataHttpClient.execute(host, get));

        verify(sstStrategy, never()).obtainSst();
        verify(httpClient, times(2)).execute(eq(host), eq(get), any(HttpContext.class));
        verify(httpClient, times(3)).execute(eq(host), any(HttpRequest.class), any(HttpContext.class));
    }

}
