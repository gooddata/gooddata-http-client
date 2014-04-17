/*
 * Copyright (C) 2007-2014, GoodData(R) Corporation. All rights reserved.
 * This program is made available under the terms of the BSD License.
 */
package com.gooddata.http.client;

import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang.Validate.notNull;

/**
 * Contains handy methods .
 */
// TODO: should better be named AuthUtils or TokenUtils or something else cookie-neutral
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

    static String extractTokenFromBody(final HttpResponse response, final String entityName) throws IOException {
        final String responseBody = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
        return extractTokenFromBody(responseBody, entityName);
    }

    static String extractTokenFromBody(final String responseBody, final String entityName) throws IOException {
        final String anyNamedValues = "(?:\\s*\"\\w+\"\\s*\\:\\s*\"[\\w/]+\"\\s*,?\\s*)*";
        final String token = "\\s*\"token\"\\s*\\:\\s*\"(\\S+?)\"\\s*,?\\s*";
        final Pattern pattern = Pattern.compile("\\s*\\{\\s*\"" + entityName + "\"\\s*\\:\\s*\\{" + anyNamedValues + token + anyNamedValues + "\\}\\s*\\}\\s*");
        final Matcher matcher = pattern.matcher(responseBody);
        if (!matcher.matches()) {
            throw new GoodDataAuthException("Unable to login. Malformed response body: " + responseBody);
        }
        return matcher.group(1);
    }
}
