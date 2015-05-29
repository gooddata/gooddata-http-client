/*
 * Copyright (C) 2007-2015, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
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
