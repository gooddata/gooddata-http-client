/*
 * Copyright (C) 2007-2013, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

import org.apache.http.HttpHost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class SimpleSSTRetrievalStrategyTest {

    public static final String TOKEN = "sst token";
    public static final String DOMAIN = "server.com";

    private HttpHost host;

    private SimpleSSTRetrievalStrategy sstStrategy;

    private DefaultHttpClient httpClient;

    @Before
    public void setUp() {
        httpClient = new DefaultHttpClient();
        host = new HttpHost("server.com", 666);
    }

    @Test
    public void obtainSst() {
        sstStrategy = new SimpleSSTRetrievalStrategy(TOKEN);
        assertEquals(TOKEN, sstStrategy.obtainSst());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullSst() {
        new SimpleSSTRetrievalStrategy(null);
    }

}
