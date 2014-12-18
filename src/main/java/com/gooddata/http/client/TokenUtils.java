/*
 * Copyright (C) 2007-2015, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contains handy methods for working with SST and TT tokens.
 */
class TokenUtils {

    private static final Pattern pattern = Pattern.compile("token: (.+)");

    private TokenUtils() { }

    static String extractToken(final HttpResponse response) throws IOException {
        final String responseBody = response == null || response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
        final Matcher matcher = pattern.matcher(responseBody);
        if (!matcher.find()) {
            throw new GoodDataAuthException("Unable to login. Malformed response body: " + responseBody);
        }
        final String token = matcher.group(1);
        if (token == null) {
            throw new GoodDataAuthException("Unable to login. Malformed response body: " + responseBody);
        }
        return token;
    }

}
