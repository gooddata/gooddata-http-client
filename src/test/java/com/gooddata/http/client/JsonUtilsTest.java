/*
 * (C) 2022 GoodData Corporation.
 * This source code is licensed under the BSD-style license found in the
 * LICENSE.txt file in the root directory of this source tree.
 */
package com.gooddata.http.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;

import static com.gooddata.http.client.JsonUtils.createLoginJson;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;
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

        assertThat(postUserLogin.path("login").asText(), is(login));
        assertThat(postUserLogin.path("password").asText(), is(password));
        assertThat(postUserLogin.path("verify_level").asInt(), is(1));
        assertThat(postUserLogin.path("remember").asInt(), is(0));
    }
}