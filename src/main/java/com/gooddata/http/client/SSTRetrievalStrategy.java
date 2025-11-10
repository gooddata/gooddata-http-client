/*
 * (C) 2022 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.client5.http.classic.HttpClient;
import java.io.IOException;
/**
 * Interface for class which encapsulates SST retrieval.
 */
public interface SSTRetrievalStrategy {

    /**
     * Obtains SST using given HTTP client and host.
     * @return SST
     * @param httpClient HTTP client
     * @param httpHost HTTP host
     */
    String obtainSst(final HttpClient httpClient, final HttpHost httpHost) throws IOException;
    /**
     * Performs the logout using given HTTP client, host and logout parameters.
     * Should throw {@link GoodDataLogoutException} in case of logout problem.
     *
     * @param httpClient HTTP client
     * @param httpHost HTTP host
     * @param url url for logout
     * @param sst SST
     * @param tt TT
     * @throws IOException in case of connection error
     * @throws GoodDataLogoutException when the logout is not possible or failed
     */
    void logout(final HttpClient httpClient, final HttpHost httpHost, final String url, final String sst, final String tt)
            throws IOException, GoodDataLogoutException;
}
