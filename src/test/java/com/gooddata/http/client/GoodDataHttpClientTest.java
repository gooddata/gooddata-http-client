/*
 * (C) 2025 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.URI;

import static com.gooddata.http.client.GoodDataHttpClient.TT_HEADER;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class GoodDataHttpClientTest {

    private static final String TT = "cookieTt";
    private static final String SST = "SST";
    @Mock
    public HttpClient httpClient;
    @Mock
    public SSTRetrievalStrategy sstStrategy;
    private GoodDataHttpClient goodDataHttpClient;
    private HttpResponse ttChallengeResponse;

    private HttpResponse sstChallengeResponse;

    private HttpResponse okResponse;

    private HttpResponse ttRefreshedResponse;

    private HttpResponse response401;

    private HttpHost host;

    private HttpGet get;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        host = new HttpHost("https", "server.com", 443);
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
        return new BasicHttpResponse(new StatusLine(new ProtocolVersion("https", 1, 1), status, reasonPhrase).getStatusCode(), reasonPhrase);
    }

    private ClassicHttpResponse createResponse(int status, String body, String reasonPhrase) {
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(status, reasonPhrase);
        if (body != null && !body.isEmpty()) {
            StringEntity entity = new StringEntity(body, ContentType.TEXT_HTML);
            response.setEntity(entity);
        }
        return response;
    }

    @Test
    public void execute_sstExpired() throws IOException {
        when(httpClient.execute(eq(host), any(ClassicHttpRequest.class), (HttpContext) isNull())) // original requests
                .thenReturn(ttChallengeResponse)
                .thenReturn(ttRefreshedResponse)
                .thenReturn(okResponse);

        assertEquals(okResponse, goodDataHttpClient.execute(host, get));

        verify(sstStrategy).obtainSst(any(HttpClient.class), any(HttpHost.class));
        verifyNoMoreInteractions(sstStrategy);
        verify(httpClient, times(2)).execute(eq(host), eq(get), (HttpContext) isNull());
        verify(httpClient, times(3)).execute(eq(host), any(ClassicHttpRequest.class), (HttpContext) isNull());
    }

    @Test
    public void execute_unableObtainSst() throws IOException {
        when(httpClient.execute(eq(host), any(ClassicHttpRequest.class), (HttpContext) isNull()))
                .thenReturn(ttChallengeResponse)
                .thenReturn(response401);

        assertEquals(response401.getCode(), goodDataHttpClient.execute(host, get).getCode());
    }

    @Test
    public void execute_unableObtainTTafterSuccessfullSstObtained() throws IOException {
        when(httpClient.execute(eq(host), any(ClassicHttpRequest.class), (HttpContext) isNull()))
                .thenReturn(ttChallengeResponse)
                .thenReturn(sstChallengeResponse)
                .thenReturn(response401);

        assertEquals(response401.getCode(), goodDataHttpClient.execute(host, get).getCode());
    }

    @Test
    public void execute_nonChallenge401() throws IOException {
        when(httpClient.execute(eq(host), eq(get), (HttpContext) isNull()))
                .thenReturn(response401);

        assertEquals(response401, goodDataHttpClient.execute(host, get));

        verifyNoInteractions(sstStrategy);
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

        verifyNoInteractions(sstStrategy);
        verify(httpClient, only()).execute(eq(host), eq(get), (HttpContext) isNull());
    }

    @Test
    public void execute_logoutPath() throws Exception {
        // first let's login
        when(httpClient.execute(eq(host), any(ClassicHttpRequest.class), (HttpContext) isNull()))
                .thenReturn(ttChallengeResponse)
                .thenReturn(ttRefreshedResponse)
                .thenReturn(okResponse);
        when(sstStrategy.obtainSst(httpClient, host)).thenReturn(SST);

        final String logoutUrl = "/gdc/account/login/1";
        final HttpResponse logoutResponse = goodDataHttpClient.execute(host, new HttpDelete(logoutUrl));
        assertEquals(204, logoutResponse.getCode());

        verify(sstStrategy).logout(eq(httpClient), eq(host), eq(logoutUrl), eq(SST), eq(TT));
    }

    @Test
    public void execute_logoutUri() throws Exception {
        // first let's login
        when(httpClient.execute(eq(host), any(ClassicHttpRequest.class), (HttpContext) isNull()))
                .thenReturn(ttChallengeResponse)
                .thenReturn(ttRefreshedResponse)
                .thenReturn(okResponse);
        when(sstStrategy.obtainSst(httpClient, host)).thenReturn(SST);

        final String logoutUri = "https://server.com:443/gdc/account/login/1";
        final HttpResponse logoutResponse = goodDataHttpClient.execute(new HttpDelete(URI.create(logoutUri)));
        assertEquals(204, logoutResponse.getCode());

        verify(sstStrategy).logout(eq(httpClient), eq(host), eq("/gdc/account/login/1"), eq(SST), eq(TT));
    }

    @Test
    public void execute_logoutFailed() throws Exception {
        // first let's login
        when(httpClient.execute(eq(host), any(ClassicHttpRequest.class), (HttpContext) isNull()))
                .thenReturn(ttChallengeResponse)
                .thenReturn(ttRefreshedResponse)
                .thenReturn(okResponse);
        when(sstStrategy.obtainSst(httpClient, host)).thenReturn(SST);

        final String logoutUrl = "/gdc/account/login/1";
        doThrow(new GoodDataLogoutException("msg", 400, "bad request"))
                .when(sstStrategy).logout(eq(httpClient), eq(host), eq(logoutUrl), eq(SST), eq(TT));

        final HttpResponse logoutResponse = goodDataHttpClient.execute(host, new HttpDelete(logoutUrl));
        assertEquals(400, logoutResponse.getCode());
    }
}
