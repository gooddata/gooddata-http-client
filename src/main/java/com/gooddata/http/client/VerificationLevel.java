/*
 * Copyright (C) 2014, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

/**
 * Indicates how credentials (SST and TT) are transferred and checked.
 */
public enum VerificationLevel {
    /** Cookie is used to transfer SST and TT. */
    COOKIE(0, true, false),

    /** SST and TT must be present in cookie as well as HTTP header. */
    HEADER_AND_COOKIE(1, true, true),

    /** SST and TT must be present in the HTTP header. */
    HEADER(2, false, true);

    private final int level;
    private final boolean cookieBased;
    private final boolean headerBased;

    private VerificationLevel(final int level, final boolean cookieBased, final boolean headerBased) {
        this.level = level;
        this.cookieBased = cookieBased;
        this.headerBased = headerBased;
    }

    /**
     * Returns value of verification level.
     * @return numeric verification level value
     */
    public int getLevel() {
        return level;
    }

    /**
     * Returns {@code true} if auth. tokens need to be present in the HTTP headers.
     * @return {@code true} if auth. tokens need to be present in the HTTP headers, otherwise returns {@false}
     */
    public boolean isHeaderBased() {
        return headerBased;
    }

    /**
     * Returns {@code true} if auth. tokens need to be present in the cookies.
     * @return {@code true} if auth. tokens need to be present in the cookies, otherwise returns {@false}
     */
    public boolean isCookieBased() {
        return cookieBased;
    }
}
