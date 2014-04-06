/*
 * Copyright (C) 2007-2014, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

import org.apache.http.client.CookieStore;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class CookieUtilsTest {

    public static final String DOMAIN = "server.com";
    public static final String SST = "sst_token";
    public static final String SST2 = "sst_token2";

    private HttpContext context;

    private CookieStore cookieStore;

    @Before
    public void setUp() {
        context = new BasicHttpContext();
        cookieStore = new BasicCookieStore();
        context.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
    }

    @Test
    public void replaceSst() {
        CookieUtils.replaceSst(SST, context, DOMAIN);
        checkCookie(SST);
    }

    @Test
    public void replaceSst_2times() {
        CookieUtils.replaceSst(SST, context, DOMAIN);
        CookieUtils.replaceSst(SST2, context, DOMAIN);
        checkCookie(SST2);
    }

    @Test
    public void replaceSst_nullSst() {
        CookieUtils.replaceSst(null, context, DOMAIN);
    }

    @Test
    public void replaceSst_nullDomain() {
        CookieUtils.replaceSst(SST, context, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void replaceSst_nullContext() {
        CookieUtils.replaceSst(SST, null, DOMAIN);
    }

    private void checkCookie(final String sst) {
        List<Cookie> cookies = cookieStore.getCookies();
        assertEquals(1, cookies.size());
        Cookie cookie = cookies.get(0);
        assertThat(DOMAIN, is(cookie.getDomain()));
        assertThat(sst, is(cookie.getValue()));
        assertThat(true, is(cookie.isSecure()));
        assertThat("/gdc/account", is(cookie.getPath()));
        assertThat("GDCAuthSST", is(cookie.getName()));
    }
}
