/*
 * (C) 2022 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.gooddata.http.client.GoodDataHttpClient.SST_HEADER;
import static com.gooddata.http.client.GoodDataHttpClient.TT_HEADER;
import static com.gooddata.http.client.TokenUtils.extractSST;
import static com.gooddata.http.client.TokenUtils.extractTT;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TokenUtilsTest {

    private ClassicHttpResponse response;

    @BeforeEach
    public void setUp() throws Exception {
        response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
    }

    @Test
    public void shouldFailOnNullResponseSST() {
        assertThrows(NullPointerException.class, () -> extractSST(null));
    }

    @Test
    public void shouldFailOnNullResponseTT() {
        assertThrows(NullPointerException.class, () -> extractTT(null));
    }

    @Test
    public void shouldFailOnMissingHeaderSST() {
        assertThrows(GoodDataAuthException.class, () -> extractSST(response));
    }

    @Test
    public void shouldFailOnMissingHeaderTT() {
        assertThrows(GoodDataAuthException.class, () -> extractTT(response));
    }

    @Test
    public void shouldExtractSST() throws Exception {
        response.addHeader(SST_HEADER, "sst");
        response.addHeader(SST_HEADER, "sst2");
        final String token = extractSST(response);
        assertThat(token, is("sst"));
    }

    @Test
    public void shouldExtractTT() throws Exception {
        response.addHeader(TT_HEADER, "tt");
        response.addHeader(TT_HEADER, "tt2");
        final String token = extractTT(response);
        assertThat(token, is("tt"));
    }
}
