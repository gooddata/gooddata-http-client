/*
 * Copyright (C) 2007-2014, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.cookie.SM;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.impl.cookie.BestMatchSpec;
import org.apache.http.protocol.HttpContext;

import java.util.List;

import static org.apache.commons.lang.Validate.notNull;

/**
 * Contains handy methods.
 */
public class CookieUtils {

    public static final String SST_COOKIE_NAME = "GDCAuthSST";
    public static final String SST_COOKIE_PATH = "/gdc/account";

    private CookieUtils() { }

    private static void replaceSst(final String sst, final CookieStore cookieStore, final String domain) {
        final BasicClientCookie cookie = new BasicClientCookie(SST_COOKIE_NAME, sst);
        cookie.setSecure(true);
        cookie.setPath(SST_COOKIE_PATH);
        cookie.setDomain(domain);
        cookieStore.addCookie(cookie);
    }

    /**
     * Add (or replace) super-secure cookie in context.
     * @param sst super-secure token
     * @param context HTTP context
     * @param domain domain
     * @throws GoodDataAuthException http client does not support cookie
     */
    static void replaceSst(final String sst, final HttpContext context, final String domain) {
        notNull(context, "Context cannot be null.");
        final CookieStore cookieStore = (CookieStore) context.getAttribute(HttpClientContext.COOKIE_STORE);
        replaceSst(sst, cookieStore, domain);
    }

    static String extractTokenFromCookie(HttpHost httpHost, HttpResponse response) {
        // SST sent only in the cookie
        try {
            final CookieSpec cookieSpec = new BestMatchSpec();
            final CookieOrigin cookieOrigin = new CookieOrigin(httpHost.getHostName(), httpHost.getPort(), "/gdc/account", true);
            for (Header header : response.getHeaders(SM.SET_COOKIE)) {
                final List<Cookie> cookies = cookieSpec.parse(header, cookieOrigin);
                if (cookies.size() > 0 && CookieUtils.SST_COOKIE_NAME.equals(cookies.get(0).getName())) {
                    return cookies.get(0).getValue();
                }
            }
        } catch (MalformedCookieException e) {
            throw new GoodDataAuthException("Unable to login. Malformed Set-Cookie header.");
        }
        return null;
    }
}
