/*
 * (C) 2025 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.StatusLine;
import org.junit.Before;
import org.junit.Test;

import static com.gooddata.http.client.GoodDataHttpClient.SST_HEADER;
import static com.gooddata.http.client.GoodDataHttpClient.TT_HEADER;
import static com.gooddata.http.client.TokenUtils.extractSST;
import static com.gooddata.http.client.TokenUtils.extractTT;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class TokenUtilsTest {

    private static final StatusLine STATUS = new StatusLine(new ProtocolVersion("http", 1, 1), HttpStatus.SC_OK, "OK");

    private BasicClassicHttpResponse response;

    @Before
    public void setUp() throws Exception {
        response = new BasicClassicHttpResponse(STATUS.getStatusCode(), STATUS.getReasonPhrase());
    }

    @Test(expected = NullPointerException.class)
    public void shouldFailOnNullResponseSST() throws Exception {
        extractSST(null);
    }

    @Test(expected = NullPointerException.class)
    public void shouldFailOnNullResponseTT() throws Exception {
        extractTT(null);
    }

    @Test(expected = GoodDataAuthException.class)
    public void shouldFailOnMissingHeaderSST() throws Exception {
        extractSST(response);
    }

    @Test(expected = GoodDataAuthException.class)
    public void shouldFailOnMissingHeaderTT() throws Exception {
        extractTT(response);
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
