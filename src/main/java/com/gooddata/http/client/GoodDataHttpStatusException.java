/*
 * (C) 2022 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;
/**
 * Exception thrown when HTTP operations fail with specific status codes.
 * This exception wraps HTTP status information to provide detailed error context.
 */
public class GoodDataHttpStatusException extends RuntimeException {

    private final int code;
    private final String reason;

    public GoodDataHttpStatusException(int code, String reason) {
        super("HTTP " + code + ": " + reason);
        this.code = code;
        this.reason = reason;
    }

    public GoodDataHttpStatusException(String message, int code, String reason) {
        super(message);
        this.code = code;
        this.reason = reason;
    }

    public GoodDataHttpStatusException(String message, Throwable cause, int code, String reason) {
        super(message, cause);
        this.code = code;
        this.reason = reason;
    }

    public int getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }
}