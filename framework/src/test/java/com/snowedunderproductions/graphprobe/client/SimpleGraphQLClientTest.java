package com.snowedunderproductions.graphprobe.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SimpleGraphQLClientTest {

    @Test
    void executeFullQuerySendsVariablesOperationNameAndPerRequestHeaders() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> requestHeader = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/graphql", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requestHeader.set(exchange.getRequestHeaders().getFirst("x-test-case"));
            send(exchange, "{\"data\":{\"user\":{\"id\":\"42\"}}}");
        });
        server.start();

        try {
            SimpleGraphQLClient client = new SimpleGraphQLClient(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/graphql",
                "Authorization",
                ""
            );

            String response = client.executeFullQuery(
                "query User($id: ID!) { user(id: $id) { id } }",
                Map.of("id", "42"),
                "User",
                Map.of("x-test-case", "variables")
            );

            assertThat(response).contains("\"id\":\"42\"");
            assertThat(requestHeader.get()).isEqualTo("variables");
            assertThat(requestBody.get())
                .contains("\"query\":\"query User($id: ID!) { user(id: $id) { id } }\"")
                .contains("\"variables\":{\"id\":\"42\"}")
                .contains("\"operationName\":\"User\"");
        } finally {
            server.stop(0);
        }
    }

    private static void send(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
