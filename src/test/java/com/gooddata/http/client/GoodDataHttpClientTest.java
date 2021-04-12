/*
 * (C) 2021 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
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
import java.net.URI;

import static com.gooddata.http.client.GoodDataHttpClient.TT_HEADER;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class GoodDataHttpClientTest {

    private static final String TT = "cookieTt";
    private static final String SST = "SST";

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
        host = new HttpHost("server.com", 443, "https");
        get = new HttpGet("/url");
        goodDataHttpClient = new GoodDataHttpClient(httpClient, host, sstStrategy);

        ttChallengeResponse = createResponse(HttpStatus.SC_UNAUTHORIZED, "<html><head><title>401 Authorization Required</title></head><body></body>", "Unauthorized");
        ttChallengeResponse.setHeader(new BasicHeader("WWW-Authenticate", "GoodData realm=\"GoodData API\" cookie=GDCAuthTT"));

        sstChallengeResponse = createResponse(HttpStatus.SC_UNAUTHORIZED, "<html><head><title>401 Authorization Required</title></head><body></body>", "Unauthorized");
        sstChallengeResponse.setHeader(new BasicHeader("WWW-Authenticate", "GoodData realm=\"GoodData API\" cookie=GDCAuthSST"));

        response401 = createResponse(HttpStatus.SC_UNAUTHORIZED, "<html><head><title>401 Authorization Required</title></head><body></body>", "Unauthorized");

        okResponse = createResponse(HttpStatus.SC_OK, "<html><head><title>OK</title></head><body></body>", "OK");

        ttRefreshedResponse = createResponse(HttpStatus.SC_OK, "OK");
        ttRefreshedResponse.setHeader(TT_HEADER, TT);
    }

    private HttpResponse createResponse(final int status, final String reasonPhrase) {
        return new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion("https", 1, 1), status, reasonPhrase));
    }

    private HttpResponse createResponse(int status, String body, String reasonPhrase) {
        HttpResponse response = createResponse(status, reasonPhrase);
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(new ByteArrayInputStream(body.getBytes()));
        response.setEntity(entity);
        return response;
    }

    @Test
    public void execute_sstExpired() throws IOException {
        when(httpClient.execute(eq(host), any(HttpRequest.class), (HttpContext) isNull())) // original requests
                .thenReturn(ttChallengeResponse)
                .thenReturn(ttRefreshedResponse)
                .thenReturn(okResponse);

        assertEquals(okResponse, goodDataHttpClient.execute(host, get));

        verify(sstStrategy).obtainSst(any(HttpClient.class), any(HttpHost.class));
        verifyNoMoreInteractions(sstStrategy);
        verify(httpClient, times(2)).execute(eq(host), eq(get), (HttpContext) isNull());
        verify(httpClient, times(3)).execute(eq(host), any(HttpRequest.class), (HttpContext) isNull());
    }

    @Test
    public void execute_unableObtainSst() throws IOException {
        when(httpClient.execute(eq(host), any(HttpRequest.class), (HttpContext) isNull()))
                .thenReturn(ttChallengeResponse)
                .thenReturn(response401);

        assertEquals(response401.getStatusLine().getStatusCode(), goodDataHttpClient.execute(host, get).getStatusLine().getStatusCode());
    }

    @Test
    public void execute_unableObtainTTafterSuccessfullSstObtained() throws IOException {
        when(httpClient.execute(eq(host), any(HttpRequest.class), (HttpContext) isNull()))
                .thenReturn(ttChallengeResponse)
                .thenReturn(sstChallengeResponse)
                .thenReturn(response401);

        assertEquals(response401.getStatusLine().getStatusCode(), goodDataHttpClient.execute(host, get).getStatusLine().getStatusCode());
    }

    @Test
    public void execute_nonChallenge401() throws IOException {
        when(httpClient.execute(eq(host), eq(get), (HttpContext) isNull()))
                .thenReturn(response401);

        assertEquals(response401, goodDataHttpClient.execute(host, get));

        verifyZeroInteractions(sstStrategy);
        verify(httpClient, only()).execute(eq(host), eq(get), (HttpContext) isNull());
    }

    /*
     * No TT or SST refresh needed.
     */
    @Test
    public void execute_okResponse() throws IOException {
        when(httpClient.execute(eq(host), eq(get), (HttpContext) isNull()))
                .thenReturn(okResponse);

        assertEquals(okResponse, goodDataHttpClient.execute(host, get));

        verifyZeroInteractions(sstStrategy);
        verify(httpClient, only()).execute(eq(host), eq(get), (HttpContext) isNull());
    }

    @Test
    public void execute_logoutPath() throws Exception {
        // first let's login
        when(httpClient.execute(eq(host), any(HttpRequestBase.class), (HttpContext) isNull()))
                .thenReturn(ttChallengeResponse)
                .thenReturn(ttRefreshedResponse)
                .thenReturn(okResponse);
        when(sstStrategy.obtainSst(httpClient, host)).thenReturn(SST);

        final String logoutUrl = "/gdc/account/login/1";
        final HttpResponse logoutResponse = goodDataHttpClient.execute(host, new HttpDelete(logoutUrl));
        assertEquals(204, logoutResponse.getStatusLine().getStatusCode());

        verify(sstStrategy).logout(eq(httpClient), eq(host), eq(logoutUrl), eq(SST), eq(TT));
    }

    @Test
    public void execute_logoutUri() throws Exception {
        // first let's login
        when(httpClient.execute(eq(host), any(HttpRequestBase.class), (HttpContext) isNull()))
                .thenReturn(ttChallengeResponse)
                .thenReturn(ttRefreshedResponse)
                .thenReturn(okResponse);
        when(sstStrategy.obtainSst(httpClient, host)).thenReturn(SST);

        final String logoutUri = "https://server.com:443/gdc/account/login/1";
        final HttpResponse logoutResponse = goodDataHttpClient.execute(new HttpDelete(URI.create(logoutUri)));
        assertEquals(204, logoutResponse.getStatusLine().getStatusCode());

        verify(sstStrategy).logout(eq(httpClient), eq(host), eq(logoutUri), eq(SST), eq(TT));
    }

    @Test
    public void execute_logoutFailed() throws Exception {
        // first let's login
        when(httpClient.execute(eq(host), any(HttpRequestBase.class), (HttpContext) isNull()))
                .thenReturn(ttChallengeResponse)
                .thenReturn(ttRefreshedResponse)
                .thenReturn(okResponse);
        when(sstStrategy.obtainSst(httpClient, host)).thenReturn(SST);

        final String logoutUrl = "/gdc/account/login/1";
        doThrow(new GoodDataLogoutException("msg", 400, "bad request"))
            .when(sstStrategy).logout(eq(httpClient), eq(host), eq(logoutUrl), eq(SST), eq(TT));

        final HttpResponse logoutResponse = goodDataHttpClient.execute(host, new HttpDelete(logoutUrl));
        assertEquals(400, logoutResponse.getStatusLine().getStatusCode());
    }
}
