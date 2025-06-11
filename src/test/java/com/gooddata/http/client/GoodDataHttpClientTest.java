/*
 * (C) 2022 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;

import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import java.lang.reflect.Field;



public class GoodDataHttpClientTest {

    private static final String TT = "cookieTt";
    private static final String SST = "SST";

    private GoodDataHttpClient goodDataHttpClient;

    @Mock
    public CloseableHttpClient httpClient;

    @Mock
    public SSTRetrievalStrategy sstStrategy;

    @Mock
    private CloseableHttpResponse ttChallengeResponse;
    
    private CloseableHttpResponse sstChallengeResponse;
    
    @Mock
    private CloseableHttpResponse okResponse;
    
    @Mock
    private CloseableHttpResponse ttRefreshedResponse;

    @Mock
    private CloseableHttpResponse response401;

    private HttpHost host;

    private HttpGet get;

    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        host = new HttpHost("https", "server.com", 443);
        get = new HttpGet("/url");
        goodDataHttpClient = new GoodDataHttpClient(httpClient, host, sstStrategy);


        when(ttChallengeResponse.getCode()).thenReturn(401);
        when(ttRefreshedResponse.getCode()).thenReturn(200);
        when(okResponse.getCode()).thenReturn(200);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void execute_sstExpired() throws IOException {
        final int[] count = {0};
        when(httpClient.execute(
                eq(host), any(HttpGet.class), (HttpContext) isNull(), any(HttpClientResponseHandler.class)))
            .thenAnswer(invocation -> {
                HttpClientResponseHandler<Object> handler = (HttpClientResponseHandler<Object>) invocation.getArgument(3);
                count[0]++;
                System.out.println("MOCK: handle call #" + count[0]);
                if (count[0] == 1) {
                    return handler.handleResponse(ttChallengeResponse); // 401 Unauthorized
                } else if (count[0] == 2) {
                    return handler.handleResponse(ttRefreshedResponse); // 200 OK
                } else {
                    return handler.handleResponse(okResponse); // OK
                }
            });

        when(sstStrategy.obtainSst(any(), any())).thenReturn("MOCKED_SST");

        assertEquals(okResponse, goodDataHttpClient.execute(host, get));
    }


    @SuppressWarnings("unchecked")
    @Test
    public void execute_unableObtainSst() throws IOException {
        when(httpClient.execute(
                eq(host),
                any(ClassicHttpRequest.class),
                (HttpContext) isNull(),
                any(HttpClientResponseHandler.class)))
            .thenAnswer(new Answer<Object>() {
                private int count = 0;
                @Override
                public Object answer(org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
                    HttpClientResponseHandler<?> handler = invocation.getArgument(3);
                    count++;
                    if (count == 1) {
                        return handler.handleResponse(ttChallengeResponse); // 401,  SST
                    } else {
                        return handler.handleResponse(response401); // 401
                    }
                }
            });

        assertEquals(response401.getCode(), goodDataHttpClient.execute(host, get).getCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void execute_unableObtainTTafterSuccessfullSstObtained() throws IOException {
        when(httpClient.execute(eq(host), any(ClassicHttpRequest.class), (HttpContext) isNull(), any(HttpClientResponseHandler.class)))
            .thenAnswer(new Answer<Object>() {
                private int count = 0;
                @Override
                public Object answer(org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
                    HttpClientResponseHandler<?> handler = invocation.getArgument(3);
                    count++;
                    if (count == 1) {
                        return handler.handleResponse(ttChallengeResponse); // 401
                    } else if (count == 2) {
                        return handler.handleResponse(sstChallengeResponse); // 401
                    } else {
                        return handler.handleResponse(response401); // 401
                    }
                }
            });

        GoodDataHttpStatusException ex = assertThrows(
            GoodDataHttpStatusException.class,
            () -> goodDataHttpClient.execute(host, get)
        );
        assertEquals(401, ex.getCode());
    }


    @SuppressWarnings("unchecked")
    @Test
    public void execute_nonChallenge401() throws IOException {
        when(httpClient.execute(
                eq(host), 
                any(ClassicHttpRequest.class), 
                (HttpContext) isNull(), 
                any(HttpClientResponseHandler.class)))
            .thenAnswer(invocation -> {
                HttpClientResponseHandler<?> handler = invocation.getArgument(3);
                return handler.handleResponse(response401);
            });

        assertEquals(response401, goodDataHttpClient.execute(host, get));
        verifyNoInteractions(sstStrategy);
        verify(httpClient, only())
            .execute(eq(host), any(ClassicHttpRequest.class), (HttpContext) isNull(), any(HttpClientResponseHandler.class));
    }


    /*
     * No TT or SST refresh needed.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void execute_okResponse() throws IOException {
        when(httpClient.execute(
                eq(host), eq(get), (HttpContext) isNull(), any(HttpClientResponseHandler.class)))
            .thenAnswer(invocation -> {
                HttpClientResponseHandler<?> handler = invocation.getArgument(3);
                return handler.handleResponse(okResponse);
            });

        assertEquals(okResponse, goodDataHttpClient.execute(host, get));

        verifyNoInteractions(sstStrategy);
        verify(httpClient, only())
            .execute(eq(host), eq(get), (HttpContext) isNull(), any(HttpClientResponseHandler.class));
    }


    @SuppressWarnings("unchecked")
    @Test
    public void execute_logoutPath() throws Exception {
        // 1. Mock httpClient.execute(...) for general HTTP behavior, as used internally by the client:
        when(httpClient.execute(eq(host), any(ClassicHttpRequest.class), (HttpContext) isNull(), any(HttpClientResponseHandler.class)))
            .thenAnswer(new Answer<Object>() {
                private int count = 0;
                @Override
                public Object answer(org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
                    HttpClientResponseHandler<?> handler = invocation.getArgument(3);
                    count++;
                    if (count == 1) {
                        // First call: simulate TT challenge (401)
                        return handler.handleResponse(ttChallengeResponse); 
                    } else if (count == 2) {
                        // Second call: simulate refreshed TT (200)
                        return handler.handleResponse(ttRefreshedResponse);
                    } else {
                        // Any subsequent calls: simulate unauthorized (401)
                        ClassicHttpResponse errorResponse = new BasicClassicHttpResponse(401, "Unauthorized");
                        return handler.handleResponse(errorResponse);
                    }
                }
            });



        // 2. Mock SST (login) retrieval to always return SST:
        when(sstStrategy.obtainSst(httpClient, host)).thenReturn(SST);

        final String logoutUrl = "/gdc/account/login/1";

        // 3. Mock logout to throw GoodDataLogoutException (this is what the test is verifying!):

        doThrow(new GoodDataLogoutException("Logout unsuccessful", 401, "Unauthorized"))
            .when(sstStrategy).logout(eq(httpClient), eq(host), eq(logoutUrl), eq(SST), eq(TT));

        System.out.println("LOOK 4: Now calling goodDataHttpClient.execute(...)");

        Field ttField = GoodDataHttpClient.class.getDeclaredField("tt");
        ttField.setAccessible(true);
        ttField.set(goodDataHttpClient, TT);
        System.out.println("DEBUG TEST: manually set tt = " + TT);

        Field sstField = GoodDataHttpClient.class.getDeclaredField("sst");
        sstField.setAccessible(true);
        sstField.set(goodDataHttpClient, SST); // Manually set SST for the test

        System.out.println("DEBUG TEST: manually set tt = " + TT + ", sst = " + SST);

        // 4. Assert that executing the client will throw GoodDataHttpStatusException with expected fields:
        GoodDataHttpStatusException ex = assertThrows(
                GoodDataHttpStatusException.class,
                () -> goodDataHttpClient.execute(host, new HttpDelete(logoutUrl))
        );
        assertEquals(401, ex.getCode());
        assertEquals("Unauthorized", ex.getReason());

        // 5. Verify that logout was actually called with the correct parameters:
        verify(sstStrategy).logout(eq(httpClient), eq(host), eq(logoutUrl), eq(SST), eq(TT));
    }



    @SuppressWarnings("unchecked")
    @Test
    public void execute_logoutUri() throws Exception {
        // first let's login
        when(httpClient.execute(
                eq(host), any(ClassicHttpRequest.class), (HttpContext) isNull(), any(HttpClientResponseHandler.class)))
            .thenAnswer(new Answer<Object>() {
                private int count = 0;
                @Override
                public Object answer(org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
                    HttpClientResponseHandler<Object> handler = (HttpClientResponseHandler<Object>) invocation.getArgument(3);
                    count++;
                    if (count == 1) {
                        return handler.handleResponse(ttChallengeResponse);
                    } else if (count == 2) {
                        return handler.handleResponse(ttRefreshedResponse);
                    } else {
                        return handler.handleResponse(okResponse);
                    }
                }
            });

        when(sstStrategy.obtainSst(httpClient, host)).thenReturn(SST);

        Field ttField = GoodDataHttpClient.class.getDeclaredField("tt");
        ttField.setAccessible(true);
        ttField.set(goodDataHttpClient, TT);    

        Field sstField = GoodDataHttpClient.class.getDeclaredField("sst");
        sstField.setAccessible(true);
        sstField.set(goodDataHttpClient, SST);      

        System.out.println("DEBUG TEST: manually set tt = " + TT + ", sst = " + SST);

        final String logoutUri = "/gdc/account/login/1";
        ClassicHttpResponse response = goodDataHttpClient.execute(host, new HttpDelete(logoutUri));
        assertEquals(204, response.getCode());
        assertEquals("Logout successful", response.getReasonPhrase());

        verify(sstStrategy).logout(eq(httpClient), eq(host), eq(logoutUri), eq(SST), eq(TT));
    }



    @SuppressWarnings("unchecked")
    @Test
    public void execute_logoutFailed() throws Exception {
        when(httpClient.execute(eq(host), any(ClassicHttpRequest.class), (HttpContext) isNull(), any(HttpClientResponseHandler.class)))
            .thenAnswer(new Answer<Object>() {
                private int count = 0;
                @Override
                public Object answer(org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
                    HttpClientResponseHandler<?> handler = invocation.getArgument(3);
                    count++;
                    if (count == 1) {
                        return handler.handleResponse(ttChallengeResponse);
                    } else if (count == 2) {
                        return handler.handleResponse(ttRefreshedResponse);
                    } else {
                        return handler.handleResponse(okResponse);
                    }
                }
            });

        when(sstStrategy.obtainSst(httpClient, host)).thenReturn(SST);

        final String logoutUrl = "/gdc/account/login/1";
        doThrow(new GoodDataLogoutException("msg", 400, "bad request"))
            .when(sstStrategy).logout(eq(httpClient), eq(host), eq(logoutUrl), eq(SST), eq(TT));

        GoodDataHttpStatusException ex = assertThrows(
            GoodDataHttpStatusException.class,
            () -> goodDataHttpClient.execute(host, new HttpDelete(logoutUrl))
        );
        assertEquals(400, ex.getCode());
        assertEquals("bad request", ex.getReason());
    }

}
