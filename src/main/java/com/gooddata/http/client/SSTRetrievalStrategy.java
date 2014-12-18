/*
 * Copyright (C) 2007-2015, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;

import java.io.IOException;

/**
 * Interface for class which encapsulates SST retrival.
 */
public interface SSTRetrievalStrategy {

    /**
     * Sets SST cookie to HTTP client.
     * @return SST
     * @param httpClient
     * @param httpHost
     */
    String obtainSst(final HttpClient httpClient, final HttpHost httpHost) throws IOException;

}
