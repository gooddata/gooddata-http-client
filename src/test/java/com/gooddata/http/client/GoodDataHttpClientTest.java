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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import java.lang.reflect.Field;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;




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
        // Initialize Mockito mocks and main GoodDataHttpClient under test
        mocks = MockitoAnnotations.openMocks(this);
        host = new HttpHost("https", "server.com", 443);
        get = new HttpGet("/url");
        goodDataHttpClient = new GoodDataHttpClient(httpClient, host, sstStrategy);

        // Always return a valid mocked response for response401 (error), never null!
        when(response401.getCode()).thenReturn(401);
        when(response401.getHeaders(anyString())).thenReturn(new Header[0]);
        when(response401.getFirstHeader(anyString())).thenReturn(null);

        // PATCH: use Mockito mock for sstChallengeResponse instead of BasicClassicHttpResponse!
        sstChallengeResponse = org.mockito.Mockito.mock(CloseableHttpResponse.class);
        when(sstChallengeResponse.getCode()).thenReturn(401);
        when(sstChallengeResponse.getHeaders(anyString()))
            .thenReturn(new Header[] { new BasicHeader("WWW-Authenticate", "cookie=GDCAuthSST") });
        when(sstChallengeResponse.getFirstHeader(anyString()))
            .thenReturn(new BasicHeader("WWW-Authenticate", "cookie=GDCAuthSST"));
        // English comment: sstChallengeResponse should always be a Mockito mock so you can use it everywhere as CloseableHttpResponse.

        // Configure ttChallengeResponse to simulate 401 Unauthorized with TT challenge header
        when(ttChallengeResponse.getCode()).thenReturn(401);
        Header ttAuthHeader = new BasicHeader("WWW-Authenticate", "cookie=GDCAuthTT");
        when(ttChallengeResponse.getHeaders("WWW-Authenticate")).thenReturn(new Header[] { ttAuthHeader });

        // Always return TT header for okResponse when requested (simulate successful re-auth)
        when(okResponse.getCode()).thenReturn(200);
        when(okResponse.getHeaders(eq("X-GDC-AuthTT"))).thenReturn(new Header[] {
            new BasicHeader("X-GDC-AuthTT", TT)
        });
        when(okResponse.getFirstHeader(eq("X-GDC-AuthTT"))).thenReturn(new BasicHeader("X-GDC-AuthTT", TT));
        // For all other headers, return empty array/null to prevent NullPointerException
        when(okResponse.getHeaders(argThat(s -> !"X-GDC-AuthTT".equals(s)))).thenReturn(new Header[0]);
        when(okResponse.getFirstHeader(argThat(s -> !"X-GDC-AuthTT".equals(s)))).thenReturn(null);

        // Configure ttRefreshedResponse to always return HTTP 200 and TT header
        when(ttRefreshedResponse.getCode()).thenReturn(200);
        when(ttRefreshedResponse.getHeaders(eq("X-GDC-AuthTT"))).thenReturn(new Header[] {
            new BasicHeader("X-GDC-AuthTT", TT)
        });
        when(ttRefreshedResponse.getFirstHeader(eq("X-GDC-AuthTT"))).thenReturn(new BasicHeader("X-GDC-AuthTT", TT));
        when(ttRefreshedResponse.getHeaders(argThat(s -> !"X-GDC-AuthTT".equals(s)))).thenReturn(new Header[0]);
        when(ttRefreshedResponse.getFirstHeader(argThat(s -> !"X-GDC-AuthTT".equals(s)))).thenReturn(null);

        // Other configuration as needed...
    }






    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }


    @Test
    public void execute_sstExpired() throws Exception {
        Field ttField = GoodDataHttpClient.class.getDeclaredField("tt");
        ttField.setAccessible(true);

        org.apache.hc.core5.http.Header ttHeader =
            new org.apache.hc.core5.http.message.BasicHeader("X-GDC-AuthTT", TT);
        when(ttRefreshedResponse.getHeaders("X-GDC-AuthTT"))
            .thenReturn(new org.apache.hc.core5.http.Header[] { ttHeader });
        when(ttRefreshedResponse.getFirstHeader("X-GDC-AuthTT"))
            .thenReturn(ttHeader);

        // English comment: The test expects three calls:
        // 1 - /url (returns 401 TT challenge)
        // 2 - /gdc/account/token (returns 200, TT refreshed)
        // 3 - /url (re-tried, returns 200 OK)
        final int[] count = {0};
        when(httpClient.execute(
            any(HttpHost.class),
            any(ClassicHttpRequest.class),
            (HttpContext) any(),
            any(HttpClientResponseHandler.class)
        )).thenAnswer(invocation -> {
            HttpClientResponseHandler handler = (HttpClientResponseHandler) invocation.getArgument(3);
            count[0]++;
            ClassicHttpRequest req = (ClassicHttpRequest) invocation.getArgument(1);
            String uri = req.getRequestUri();

            // 1st call: /url - TT challenge (401)
            if (count[0] == 1 && uri.equals("/url")) {
                return handler.handleResponse(ttChallengeResponse);
            }
            // 2nd call: /gdc/account/token - refresh TT
            else if (count[0] == 2 && uri.equals("/gdc/account/token")) {
                // Set TT after refresh
                ttField.set(goodDataHttpClient, TT);
                return handler.handleResponse(ttRefreshedResponse);
            }
            // 3rd call: /url - retry, should return OK
            else if (count[0] == 3 && uri.equals("/url")) {
                return handler.handleResponse(okResponse);
            } else {
                throw new AssertionError("Too many calls to httpClient.execute! count=" + count[0] + ", uri=" + uri);
            }
        });

        when(sstStrategy.obtainSst(httpClient, host)).thenReturn("MOCKED_SST");

        final ClassicHttpRequest get = new HttpGet("/url");

        ClassicHttpResponse result = goodDataHttpClient.execute(host, get);

        assertEquals(okResponse, result);
        assertEquals(3, count[0]); // English comment: There should be exactly 3 httpClient.execute calls!
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
                        return handler.handleResponse(ttChallengeResponse); // 401, SST
                    } else {
                        return handler.handleResponse(response401); // 401
                    }
                }
            });
        GoodDataAuthException ex = assertThrows(
            GoodDataAuthException.class,
            () -> goodDataHttpClient.execute(host, get)
        );
        assertTrue(ex.getMessage().contains("Unable to obtain TT after successfully obtained SST"));
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
                        return handler.handleResponse(ttChallengeResponse); // 401 TT challenge
                    } else if (count == 2) {
                        return handler.handleResponse(sstChallengeResponse); // 401 SST challenge
                    } else {
                        return handler.handleResponse(response401); // fallback
                    }
                }
            });


        GoodDataAuthException ex = assertThrows(
            GoodDataAuthException.class,
            () -> goodDataHttpClient.execute(host, get)
        );
        // Можно проверить текст:
        assertTrue(ex.getMessage().contains("Unable to obtain TT"));
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


        Field ttField = GoodDataHttpClient.class.getDeclaredField("tt");
        ttField.setAccessible(true);
        ttField.set(goodDataHttpClient, TT);

        Field sstField = GoodDataHttpClient.class.getDeclaredField("sst");
        sstField.setAccessible(true);
        sstField.set(goodDataHttpClient, SST); // Manually set SST for the test


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

        final String logoutUri = "/gdc/account/login/1";
        ClassicHttpResponse response = goodDataHttpClient.execute(host, new HttpDelete(logoutUri));
        assertEquals(204, response.getCode());
        assertEquals("Logout successful", response.getReasonPhrase());

        verify(sstStrategy).logout(eq(httpClient), eq(host), eq(logoutUri), eq(SST), eq(TT));
    }



    @SuppressWarnings("unchecked")
    @Test
    public void execute_logoutFailed() throws Exception {
        // English comment: Prepare the mock sequence for httpClient.execute as in the working tests
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

        // --- PATCH: Manually set TT and SST fields before logout call ---
        Field ttField = GoodDataHttpClient.class.getDeclaredField("tt");
        ttField.setAccessible(true);
        ttField.set(goodDataHttpClient, TT);

        Field sstField = GoodDataHttpClient.class.getDeclaredField("sst");
        sstField.setAccessible(true);
        sstField.set(goodDataHttpClient, SST);

        // --- Prepare logout to throw exception (this is what the test is verifying) ---
        final String logoutUrl = "/gdc/account/login/1";
        doThrow(new GoodDataLogoutException("msg", 400, "bad request"))
            .when(sstStrategy).logout(eq(httpClient), eq(host), eq(logoutUrl), eq(SST), eq(TT));

        // English comment: Now, when execute is called, it will try to logout with SST/TT, which is mocked to throw!
        GoodDataHttpStatusException ex = assertThrows(
            GoodDataHttpStatusException.class,
            () -> goodDataHttpClient.execute(host, new org.apache.hc.client5.http.classic.methods.HttpDelete(logoutUrl))
        );
        assertEquals(400, ex.getCode());
        assertEquals("bad request", ex.getReason());
    }


}
