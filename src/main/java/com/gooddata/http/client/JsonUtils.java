/**
 * Copyright (C) 2007-2016, GoodData(R) Corporation. All rights reserved.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.apache.commons.lang.StringEscapeUtils;

/**
 * Internal JSON helper
 */
class JsonUtils {

    static String createLoginJson(final String login, final String password, final int verificationLevel) {
        return "{\"postUserLogin\":{\"login\":\"" + StringEscapeUtils.escapeJava(login) +
                "\",\"password\":\"" + StringEscapeUtils.escapeJava(password) + "\",\"remember\":0" +
                ",\"verify_level\":" + verificationLevel + "}}";
    }
}
