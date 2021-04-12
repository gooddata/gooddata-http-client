/*
 * (C) 2021 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
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

    @Test(expected = NullPointerException.class)
    public void constructor_nullSst() {
        new SimpleSSTRetrievalStrategy(null);
    }

    @Test
    public void shouldLogout() throws Exception {
        SimpleSSTRetrievalStrategy sstStrategy = new SimpleSSTRetrievalStrategy(TOKEN);
        sstStrategy.logout(null, null, null, null, null);
    }
}
