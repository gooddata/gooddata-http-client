/*
 * Copyright (C) 2014, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

/**
 * Indicates how credentials (SST and TT) are transfered and checked.
 */
public enum VerificationLevel {
    /** Cookie is used to transfer SST and TT. */
    COOKIE(0),

    /** SST and TT must be present in cookie as well as HTTP header. */
    HEADER_AND_COOKIE(1),

    /** SST and TT must be present in the HTTP header. */
    HEADER(2);

    private final int level;

    private VerificationLevel(final int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
