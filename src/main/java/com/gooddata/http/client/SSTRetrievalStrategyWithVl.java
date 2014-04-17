/*
 * Copyright (C) 2014, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

import org.apache.http.HttpHost;

/**
 * Interface for class which encapsulates SST retrieval with support for verification levels.
 */
public interface SSTRetrievalStrategyWithVl extends SSTRetrievalStrategy {

    /**
     * Sets SST cookie to HTTP client.
     * @param verificationLevel Verification level.
     * @return SST
     */
    String obtainSst(VerificationLevel verificationLevel);

    /**
     * Returns host to be used to retrieve TT tokens.
     * @return http host
     */
    HttpHost getTokenHost();

    /**
     * Returns default verification level to be used with this strategy if no verification level is specified explicitly
     * by the user.
     * @return verification level to be used by default
     */
    VerificationLevel getDefaultVerificationLevel();
}
