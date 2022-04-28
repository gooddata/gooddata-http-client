/*
 * (C) 2022 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.apache.commons.text.StringEscapeUtils;

/**
 * Internal JSON helper
 */
class JsonUtils {

    private JsonUtils() {}

    static String createLoginJson(final String login, final String password, final int verificationLevel) {
        return "{\"postUserLogin\":{\"login\":\"" + StringEscapeUtils.escapeJava(login) +
                "\",\"password\":\"" + StringEscapeUtils.escapeJava(password) + "\",\"remember\":0" +
                ",\"verify_level\":" + verificationLevel + "}}";
    }
}
