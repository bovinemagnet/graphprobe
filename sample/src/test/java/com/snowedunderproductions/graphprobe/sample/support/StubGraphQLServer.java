package com.snowedunderproductions.graphprobe.sample.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal, in-process GraphQL endpoint built on the JDK {@link HttpServer},
 * used by the GraphProbe sample tests so they run fully offline.
 *
 * <p>Requests are routed by inspecting the serialised query body (substring match,
 * not a real GraphQL parser):
 * <ul>
 *   <li>{@code addProduct(} &rarr; a validation error response</li>
 *   <li>{@code products(} &rarr; a page of products honouring {@code pageNumber} /
 *       {@code pageSize}</li>
 *   <li>{@code product(} &rarr; a single product with a nested owner and tags</li>
 * </ul>
 *
 * <p>{@link #failFirst(int)} makes the next <em>n</em> requests return HTTP 503,
 * to exercise the resilient client's retry path.
 *
 * @author Paul Snow
 * @since 0.0.0
 */
public final class StubGraphQLServer {

    private static final Pattern PAGE_NUMBER = Pattern.compile("pageNumber:\\s*(\\d+)");
    private static final Pattern PAGE_SIZE = Pattern.compile("pageSize:\\s*(\\d+)");

    private final HttpServer server;
    private final AtomicInteger failuresRemaining = new AtomicInteger(0);
    private volatile int productTotal = 7;

    private StubGraphQLServer(HttpServer server) {
        this.server = server;
    }

    /**
     * Starts a stub bound to an ephemeral loopback port.
     *
     * @return a running stub; the caller must {@link #stop()} it
     * @throws IOException if the server cannot bind
     */
    public static StubGraphQLServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        StubGraphQLServer stub = new StubGraphQLServer(server);
        server.createContext("/graphql", stub::handle);
        server.start();
        return stub;
    }

    /** @return the {@code http://127.0.0.1:<port>/graphql} endpoint URL */
    public String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/graphql";
    }

    /**
     * Makes the next {@code n} requests respond with HTTP 503 before serving normally.
     *
     * @param n number of requests to fail
     * @return this, for chaining
     */
    public StubGraphQLServer failFirst(int n) {
        failuresRemaining.set(n);
        return this;
    }

    /**
     * Sets the total number of products the {@code products} query pages through.
     *
     * @param total total product count
     * @return this, for chaining
     */
    public StubGraphQLServer productTotal(int total) {
        this.productTotal = total;
        return this;
    }

    /** Stops the server immediately. */
    public void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        if (failuresRemaining.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            send(exchange, 503, "{\"errors\":[{\"message\":\"service unavailable\"}]}");
            return;
        }

        String response;
        if (body.contains("addProduct(")) {
            response = "{\"errors\":[{\"message\":\"input must not be null\"}]}";
        } else if (body.contains("products(")) {
            response = productsPage(body);
        } else if (body.contains("product(")) {
            response = "{\"data\":{\"product\":" + product(1) + "}}";
        } else {
            response = "{\"errors\":[{\"message\":\"unknown query\"}]}";
        }
        send(exchange, 200, response);
    }

    private String productsPage(String body) {
        int pageNumber = intFrom(PAGE_NUMBER, body, 0);
        int pageSize = intFrom(PAGE_SIZE, body, 100);
        int start = pageNumber * pageSize;
        int end = Math.min(start + pageSize, productTotal);
        StringBuilder items = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (items.length() > 0) {
                items.append(',');
            }
            items.append(product(i));
        }
        return "{\"data\":{\"products\":[" + items + "]}}";
    }

    private String product(int n) {
        return "{\"id\":\"" + n + "\",\"name\":\"Product " + n + "\",\"price\":" + n + ".0,"
            + "\"owner\":{\"id\":\"o" + n + "\",\"name\":\"Ada Lovelace\","
            + "\"email\":\"ada" + n + "@example.com\"},"
            + "\"tags\":[\"featured\",\"sale\"]}";
    }

    private static int intFrom(Pattern pattern, String body, int fallback) {
        Matcher m = pattern.matcher(body);
        return m.find() ? Integer.parseInt(m.group(1)) : fallback;
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
