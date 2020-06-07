/*
 * Copyright 2017-2020 Emmanuel Keller / Jaeksoft
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jaeksoft.opensearchserver;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ServerTest {

    protected static Client client;

    @BeforeClass
    public static void setup() throws IOException {
        client = ClientBuilder.newClient();
        final Path dataDir = Files.createTempDirectory("server-test-data");
        System.setProperty("QWAZR_DATA", dataDir.toString());
        Files.copy(Paths.get("config.properties"), dataDir.resolve("config.properties"));
    }

    @AfterClass
    public static void cleanup() {
        if (client != null)
            client.close();
    }

    @Test
    public void checkThatTheServerStarts() throws Exception {
        // Start the server
        Server.main();
        Assert.assertNotNull(Server.getInstance());

        // Check if the favicon is returned
        try (final Response response  = client.target("http://localhost:9090")
            .path("/favicon.ico")
            .request().get()) {
            Assert.assertEquals(response.getStatus(),  200);
        }

        // Stop the server
        Server.stop();
        Assert.assertNull(Server.getInstance());

    }
}
