/**
 * Copyright (C) 2007-2016, GoodData(R) Corporation. All rights reserved.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

public class GoodDataLogoutExceptionMatcher extends BaseMatcher<GoodDataLogoutException> {

    private final int statusCode;
    private final String statusText;

    public GoodDataLogoutExceptionMatcher(int statusCode) {
        this(statusCode, null);
    }

    public GoodDataLogoutExceptionMatcher(int statusCode, String statusText) {
        this.statusCode = statusCode;
        this.statusText = statusText;
    }

    @Override
    public boolean matches(Object o) {
        if (o instanceof GoodDataLogoutException) {
            final GoodDataLogoutException e = (GoodDataLogoutException) o;
            return statusCode == e.getStatusCode() && (statusText == null || statusText.equals(e.getStatusText()));
        }
        return false;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(GoodDataLogoutException.class.getName());
        description.appendText(" with\n");
        description.appendText("\tstatus code: ").appendValue(statusCode).appendText("\n");
        if (statusText != null) {
            description.appendText("\tstatus text: ").appendValue(statusText);
        }
    }
}
