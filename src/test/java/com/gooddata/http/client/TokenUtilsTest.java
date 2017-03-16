/**
 * Copyright (C) 2007-2016, GoodData(R) Corporation. All rights reserved.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.Before;
import org.junit.Test;

import static com.gooddata.http.client.GoodDataHttpClient.SST_HEADER;
import static com.gooddata.http.client.GoodDataHttpClient.TT_HEADER;
import static com.gooddata.http.client.TokenUtils.extractSST;
import static com.gooddata.http.client.TokenUtils.extractTT;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class TokenUtilsTest {

    private static final BasicStatusLine STATUS = new BasicStatusLine(new ProtocolVersion("http", 1, 1), HttpStatus.SC_OK, "OK");

    private BasicHttpResponse response;

    @Before
    public void setUp() throws Exception {
        response = new BasicHttpResponse(STATUS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailOnNullResponseSST() throws Exception {
        extractSST(null);
    }

    @Test(expected = IllegalArgumentException.class)
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
