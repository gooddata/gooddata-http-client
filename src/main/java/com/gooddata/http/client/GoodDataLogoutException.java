/**
 * Copyright (C) 2007-2016, GoodData(R) Corporation. All rights reserved.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

/**
 * Should be thrown when logout is not successful or not possible.
 * Must be provided with statusCode and optionally statusText, which are used to construct http response, for logout request.
 */
public class GoodDataLogoutException extends Exception {

    private static final String DEFAULT_STATUS_TEXT = "Logout failure";

    private final int statusCode;
    private final String statusText;

    public GoodDataLogoutException(String message, int statusCode) {
        this(message, statusCode, DEFAULT_STATUS_TEXT);
    }

    public GoodDataLogoutException(String message, int statusCode, String statusText) {
        super(message);
        this.statusCode = statusCode;
        this.statusText = statusText;
    }

    public GoodDataLogoutException(String message, Throwable cause, int statusCode) {
        this(message, cause, statusCode, DEFAULT_STATUS_TEXT);
    }

    public GoodDataLogoutException(String message, Throwable cause, int statusCode, String statusText) {
        super(message, cause);
        this.statusCode = statusCode;
        this.statusText = statusText;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusText() {
        return statusText;
    }
}
