/*
 * Copyright (C) 2007-2013, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

/**
 * Interface for class which encapsulates SST retrieval.
 * @deprecated Use static methods in {@link com.gooddata.http.client.GoodDataHttpClient} to create new clients. You can subclass
 * {@link GoodDataHttpClient} directly to override the default SST retrieval strategies.
 */
public interface SSTRetrievalStrategy {

    /**
     * Sets SST cookie to HTTP client.
     * @return SST
     */
    String obtainSst();

}
