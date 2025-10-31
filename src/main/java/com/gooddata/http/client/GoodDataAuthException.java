/*
 * (C) 2022 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;
/**
 * GoodData authentication exception.
 */
public class GoodDataAuthException extends RuntimeException {

    public GoodDataAuthException() { }
    public GoodDataAuthException(String message) {
        super(message);
    }

    public GoodDataAuthException(String message, Throwable cause) {
        super(message, cause);
    }

    public GoodDataAuthException(Throwable cause) {
        super(cause);
    }

}
