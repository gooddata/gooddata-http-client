/*
 * Copyright (C) 2007-2013, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

import static org.apache.commons.lang.Validate.notNull;

/**
 * Provides super-secure token (SST)
 * @deprecated Use static methods in {@link com.gooddata.http.client.GoodDataHttpClient} to create new clients. You can subclass
 * {@link GoodDataHttpClient} directly to override the default SST retrieval strategies.
 */
public class SimpleSSTRetrievalStrategy implements SSTRetrievalStrategy {

    private final String sst;

    /**
     * Creates new instance.
     * @param sst super-secure token (SST)
     */
    public SimpleSSTRetrievalStrategy(final String sst) {
        notNull(sst, "No SST set.");
        this.sst = sst;
    }

    @Override
    public String obtainSst() {
        return sst;
    }

}
