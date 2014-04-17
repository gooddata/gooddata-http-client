/*
 * Copyright (C) 2007-2014, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class SimpleSSTRetrievalStrategyTest {

    public static final String TOKEN = "sst token";

    @Test
    public void obtainSst() {
        SimpleSSTRetrievalStrategy sstStrategy = new SimpleSSTRetrievalStrategy(TOKEN);
        assertEquals(TOKEN, sstStrategy.obtainSst());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullSst() {
        new SimpleSSTRetrievalStrategy(null);
    }

}
