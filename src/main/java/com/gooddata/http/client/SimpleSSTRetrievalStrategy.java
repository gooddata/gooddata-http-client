/*
 * (C) 2022 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import static org.apache.commons.lang3.Validate.notNull;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.client5.http.classic.HttpClient;

import java.io.IOException;

/**
 * Provides super-secure token (SST).
 * This class is intended only for limited use mostly in tests and similar situations, since it doesn't know where the SST came from,
 * it is not capable to perform proper logout (implemented as noop).
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
    public String obtainSst(final HttpClient httpClient, final HttpHost httpHost) {
        return sst;
    }

    /**
     * NO-OP as it cannot delete SST, because it is not known where it came from.
     * @param httpClient ignored
     * @param httpHost ignored
     * @param url ignored
     * @param sst ignored
     * @param tt ignored
     * @throws IOException never
     * @throws GoodDataLogoutException never
     */
    @Override
    public void logout(HttpClient httpClient, HttpHost httpHost, String url, String sst, String tt) throws IOException, GoodDataLogoutException {
        // does nothing
    }


}
