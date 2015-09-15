/*
 * Copyright (C) 2007-2015, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

import static org.junit.Assert.assertEquals;

import org.junit.Test;


public class SimpleSSTRetrievalStrategyTest {

    public static final String TOKEN = "sst token";

    @Test
    public void obtainSst() {
        SimpleSSTRetrievalStrategy sstStrategy = new SimpleSSTRetrievalStrategy(TOKEN);
        assertEquals(TOKEN, sstStrategy.obtainSst(null, null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullSst() {
        new SimpleSSTRetrievalStrategy(null);
    }

    @Test
    public void shouldLogout() throws Exception {
        SimpleSSTRetrievalStrategy sstStrategy = new SimpleSSTRetrievalStrategy(TOKEN);
        sstStrategy.logout(null, null, null, null, null);
    }
}
