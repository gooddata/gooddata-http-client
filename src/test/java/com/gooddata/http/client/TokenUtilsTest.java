/**
 * Copyright (C) 2007-2016, GoodData(R) Corporation. All rights reserved.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.junit.Test;

import java.io.UnsupportedEncodingException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenUtilsTest {

    @Test
    public void shouldExtractSST() throws Exception {
        String tt = TokenUtils.extractToken(response("--- \n" +
                "userLogin: \n" +
                "  profile: /gdc/account/profile/1\n" +
                "  state: /gdc/account/login/1\n" +
                "  token: nbWW7peskrKbSMYj"));
        assertEquals("nbWW7peskrKbSMYj", tt);
    }

    @Test
    public void shouldExtractSingleQuotedSST() throws Exception {
        String tt = TokenUtils.extractToken(response("--- \n" +
                "userLogin: \n" +
                "  profile: '/gdc/account/profile/1'\n" +
                "  token: 'nbWW7peskrKbSMYj'\n" +
                "  state: '/gdc/account/login/1'"));
        assertEquals("nbWW7peskrKbSMYj", tt);
    }

    @Test
    public void shouldExtractQuotedSST() throws Exception {
        String tt = TokenUtils.extractToken(response("--- \n" +
                "userLogin: \n" +
                "  profile: \"/gdc/account/profile/1\"\n" +
                "  state: \"/gdc/account/login/1\"\n" +
                "  token: \"nbWW7peskrKbSMYj\""));
        assertEquals("nbWW7peskrKbSMYj", tt);
    }

    @Test
    public void shouldExtractTT() throws Exception {
        String tt = TokenUtils.extractToken(response("---\n" +
                "  userToken:\n" +
                "    token: nbWW7peskrKbSMYj"));
        assertEquals("nbWW7peskrKbSMYj", tt);
    }

    @Test
    public void shouldExtractQuotedTT() throws Exception {
        String tt = TokenUtils.extractToken(response("---\n" +
                "  userToken:\n" +
                "    token: \"nbWW7peskrKbSMYj\""));
        assertEquals("nbWW7peskrKbSMYj", tt);
    }

    @Test(expected = GoodDataAuthException.class)
    public void shouldFailOnEmptyString() throws Exception {
        TokenUtils.extractToken(response(""));
    }

    @Test(expected = GoodDataAuthException.class)
    public void shouldFailOnEmptyToken() throws Exception {
        TokenUtils.extractToken(response("token: "));
    }

    @Test(expected = GoodDataAuthException.class)
    public void shouldFailOnInvalid() throws Exception {
        TokenUtils.extractToken(response("foo"));
    }

    private static HttpResponse response(final String body) throws UnsupportedEncodingException {
        final HttpResponse response = mock(HttpResponse.class);
        when(response.getEntity()).thenReturn(new StringEntity(body));
        return response;
    }
}
