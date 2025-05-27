/*
 * (C) 2022 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

@SuppressWarnings("squid:S2699")
public class SimpleSSTRetrievalStrategyTest {

    public static final String TOKEN = "sst token";

    @Test
    public void obtainSst() {
        SimpleSSTRetrievalStrategy sstStrategy = new SimpleSSTRetrievalStrategy(TOKEN);
        assertEquals(TOKEN, sstStrategy.obtainSst(null, null));
    }

@Test
void constructor_nullSst() {
    assertThrows(NullPointerException.class, () -> new SimpleSSTRetrievalStrategy(null));
}

    @Test
    public void shouldLogout() throws Exception {
        SimpleSSTRetrievalStrategy sstStrategy = new SimpleSSTRetrievalStrategy(TOKEN);
        sstStrategy.logout(null, null, null, null, null);
    }
}
