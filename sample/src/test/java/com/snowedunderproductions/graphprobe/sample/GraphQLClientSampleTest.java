package com.snowedunderproductions.graphprobe.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.snowedunderproductions.graphprobe.client.GraphQLTestClient;
import com.snowedunderproductions.graphprobe.client.TestResponse;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Demonstrates driving {@link GraphQLTestClient} against a GraphQL endpoint.
 *
 * <p>The endpoint here is a minimal in-process stub built on the JDK's
 * {@link HttpServer} bound to an ephemeral port, so the test runs offline with no
 * external services. A real consumer would point the client at their service URL
 * (typically via the {@code GRAPHQL_URL} environment variable and the no-arg
 * constructor); this sample uses the explicit-URL constructor so the stub's
 * random port can be injected directly.
 */
class GraphQLClientSampleTest {

    private HttpServer server;
    private String endpoint;
    private volatile String nextResponseBody;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/graphql", exchange -> {
            byte[] body = nextResponseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/graphql";
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void executeQueryExtractsDataByJsonPath() throws IOException {
        nextResponseBody = "{\"data\":{\"user\":{\"name\":\"Ada Lovelace\"}}}";

        GraphQLTestClient client = new GraphQLTestClient(endpoint, "Authorization", "");

        String name = client.executeQuery(
            "{ user(id: \"1\") { name } }",
            "$.data.user.name"
        );

        assertThat(name).isEqualTo("Ada Lovelace");
    }

    @Test
    void executeQueryOrPossibleSpecificErrorRecognisesTheExpectedError()
        throws IOException {
        nextResponseBody = "{\"errors\":[{\"message\":\"User not found\"}]}";

        GraphQLTestClient client = new GraphQLTestClient(endpoint, "Authorization", "");

        TestResponse response = client.executeQueryOrPossibleSpecificError(
            "{ user(id: \"999\") { name } }",
            Optional.of("$.data.user"),
            Optional.of("User not found")
        );

        assertThat(response.success()).isTrue();
        assertThat(response.error()).contains("User not found");
    }
}
