/*
 * (C) 2025 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import org.junit.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static com.gooddata.http.client.JsonUtils.createLoginJson;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

public class JsonUtilsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void shouldCreateLoginJson() throws Exception {
        assertLoginJson("roman@gooddata.com", "Roman1");
        assertLoginJson("roman@gooddata.com", "{Roman1}");
        assertLoginJson("roman@gooddata.com", "Roman\"1");
        assertLoginJson("roman@gooddata.com", "Roman'1");
    }

    private void assertLoginJson(final String login, final String password) throws IOException {
        final String json = createLoginJson(login, password, 1);
        final JsonNode node = MAPPER.readTree(json);

        final JsonNode postUserLogin = node.path("postUserLogin");
        assertTrue(postUserLogin.isObject());

        assertThat(postUserLogin.path("login").asString(), is(login));
        assertThat(postUserLogin.path("password").asString(), is(password));
        assertThat(postUserLogin.path("verify_level").asInt(), is(1));
        assertThat(postUserLogin.path("remember").asInt(), is(0));
    }
}