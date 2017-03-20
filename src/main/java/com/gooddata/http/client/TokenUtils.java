/**
 * Copyright (C) 2007-2016, GoodData(R) Corporation. All rights reserved.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.apache.http.Header;
import org.apache.http.HttpResponse;

import static com.gooddata.http.client.GoodDataHttpClient.SST_HEADER;
import static com.gooddata.http.client.GoodDataHttpClient.TT_HEADER;
import static org.apache.commons.lang3.Validate.notNull;

/**
 * Contains handy methods for working with SST and TT tokens.
 */
class TokenUtils {

    static String extractSST(final HttpResponse response) {
        return extractToken(response, SST_HEADER);
    }

    static String extractTT(final HttpResponse response) {
        return extractToken(response, TT_HEADER);
    }

    private static String extractToken(final HttpResponse response, final String headerName) {
        notNull(response, "response can't be null");
        notNull(headerName, "headerName can't be null");
        final Header header = response.getFirstHeader(headerName);
        if (header == null) {
            throw new GoodDataAuthException("Unable to login. Response doesn't contain header " + headerName);
        }
        return header.getValue();
    }
}
