/*
 * Copyright (C) 2014, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

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
}
