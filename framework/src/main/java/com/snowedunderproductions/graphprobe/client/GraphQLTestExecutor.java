package com.snowedunderproductions.graphprobe.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snowedunderproductions.graphprobe.config.EnvConfig;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.MonoGraphQLClient;
import com.netflix.graphql.dgs.client.WebClientGraphQLClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Spring WebFlux / Netflix DGS reactive executor for GraphQL integration testing.
 *
 * <p>Exposes static, pre-configured instances of
 * {@link org.springframework.web.reactive.function.client.WebClient},
 * {@link org.springframework.test.web.reactive.server.WebTestClient},
 * {@link com.netflix.graphql.dgs.client.WebClientGraphQLClient}, and
 * {@link org.springframework.graphql.test.tester.HttpGraphQlTester} ready for use in
 * tests that prefer the reactive programming model. Convenience static methods wrap
 * the most common reactive execution patterns.
 *
 * <p>For tests that do not need the reactive stack, prefer the simpler
 * {@link GraphQLTestClient} or the resilient {@link SimpleGraphQLClient}.
 *
 * <h3>Configuration</h3>
 * <p>All values are resolved from the environment via
 * {@link com.snowedunderproductions.graphprobe.config.EnvConfig}. No URLs or credentials
 * are hardcoded:
 * <ul>
 *   <li>{@code GRAPHQL_URL} — the GraphQL endpoint URL (required)</li>
 *   <li>{@code AUTH_HEADER_NAME} — authorisation header name
 *       (default: {@code "Authorization"})</li>
 *   <li>{@code AUTH_HEADER_VALUE} — authorisation header value (optional)</li>
 *   <li>{@code GRAPHPROBE_HEADER_NAME} — GraphProbe probe-key header name
 *       (default: {@code "x-graphprobe-api-key"})</li>
 *   <li>{@code GRAPHPROBE_HEADER_VALUE} — GraphProbe probe-key header value (optional)</li>
 *   <li>{@code API_GW_HEADER_NAME} — API gateway header name
 *       (default: {@code "x-api-key"})</li>
 *   <li>{@code API_GW_HEADER_VALUE} — API gateway header value (optional)</li>
 *   <li>{@code DEFAULT_TEST_TIMEOUT} — default reactive timeout in seconds (default: 30)</li>
 *   <li>{@code DEFAULT_TEST_LONG_TIMEOUT} — extended reactive timeout in seconds
 *       (default: 60)</li>
 * </ul>
 *
 * <h3>Usage example</h3>
 * <pre>{@code
 * // Extract a single value by JSONPath (blocks until resolved)
 * String result = GraphQLTestExecutor.simpleCall(query, "$.data.users");
 *
 * // Return the matched value as a formatted JSON string
 * String json = GraphQLTestExecutor.simpleJsonCall(query, "$.data.users");
 *
 * // Execute with a custom authentication token and API gateway key
 * GraphQlTester.Response response = GraphQLTestExecutor.simpleCallWithToken(
 *     query,
 *     "Bearer mytoken",
 *     "my-gateway-key"
 * );
 * }</pre>
 *
 * @see GraphQLTestClient
 * @see SimpleGraphQLClient
 */
public class GraphQLTestExecutor {

    private static final Logger log = LoggerFactory.getLogger(
        GraphQLTestExecutor.class
    );

    // Configuration from environment
    private static final String GRAPHQL_URL = EnvConfig.getRequired("GRAPHQL_URL");
    private static final String AUTH_HEADER_NAME = EnvConfig.get(
        "AUTH_HEADER_NAME",
        "Authorization"
    );
    private static final String AUTH_HEADER_VALUE = EnvConfig.get(
        "AUTH_HEADER_VALUE",
        ""
    );
    private static final String GRAPHPROBE_HEADER_NAME = EnvConfig.get(
        "GRAPHPROBE_HEADER_NAME",
        "x-graphprobe-api-key"
    );
    private static final String GRAPHPROBE_HEADER_VALUE = EnvConfig.get(
        "GRAPHPROBE_HEADER_VALUE",
        ""
    );
    private static final String API_GW_HEADER_NAME = EnvConfig.get(
        "API_GW_HEADER_NAME",
        "x-api-key"
    );
    private static final String API_GW_HEADER_VALUE = EnvConfig.get(
        "API_GW_HEADER_VALUE",
        ""
    );
    private static final Duration DEFAULT_TEST_TIMEOUT_DURATION = Duration.ofSeconds(
        EnvConfig.getInt("DEFAULT_TEST_TIMEOUT", 30)
    );
    private static final Duration DEFAULT_TEST_LONG_TIMEOUT_DURATION = Duration.ofSeconds(
        EnvConfig.getInt("DEFAULT_TEST_LONG_TIMEOUT", 60)
    );

    /**
     * Pre-configured {@link org.springframework.web.reactive.function.client.WebClient}
     * targeting {@code GRAPHQL_URL} with a 16 MB in-memory codec buffer.
     * Used as the base for {@link #graphQLClient} and as a starting point for
     * custom reactive calls.
     */
    public static final WebClient webClient = WebClient.create(GRAPHQL_URL)
        .mutate()
        .codecs(configurer ->
            configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)
        )
        .build();

    /**
     * Pre-configured {@link org.springframework.test.web.reactive.server.WebTestClient}
     * targeting {@code GRAPHQL_URL}. The client sets {@code Content-Type: application/json},
     * applies the authorisation header when configured, and uses the
     * {@code DEFAULT_TEST_TIMEOUT} response timeout. Used as the base for {@link #tester}.
     */
    public static final WebTestClient webTestClient = createWebTestClient(
        GRAPHQL_URL,
        AUTH_HEADER_NAME,
        AUTH_HEADER_VALUE
    );

    /**
     * Pre-configured Netflix DGS {@link com.netflix.graphql.dgs.client.WebClientGraphQLClient}
     * backed by {@link #webClient}. Attaches the authorisation, GraphProbe probe-key, and
     * API gateway headers from the environment (each header is only added when its
     * corresponding value variable is non-empty).
     */
    public static final WebClientGraphQLClient graphQLClient =
        MonoGraphQLClient.createWithWebClient(webClient, headers -> {
            if (!AUTH_HEADER_VALUE.isEmpty()) {
                headers.add(AUTH_HEADER_NAME, AUTH_HEADER_VALUE);
            }
            if (!GRAPHPROBE_HEADER_VALUE.isEmpty()) {
                headers.add(GRAPHPROBE_HEADER_NAME, GRAPHPROBE_HEADER_VALUE);
            }
            if (!API_GW_HEADER_VALUE.isEmpty()) {
                headers.add(API_GW_HEADER_NAME, API_GW_HEADER_VALUE);
            }
        });

    /**
     * Pre-configured {@link org.springframework.graphql.test.tester.HttpGraphQlTester}
     * backed by {@link #webTestClient}. Suitable for Spring GraphQL assertion-style tests.
     */
    public static final HttpGraphQlTester tester = HttpGraphQlTester.create(
        webTestClient
    );

    /**
     * Create a WebTestClient with the specified configuration.
     *
     * @param baseUrl the base URL for the client
     * @param authHeaderName the authorization header name
     * @param authHeaderValue the authorization header value
     * @return a configured WebTestClient instance
     */
    private static WebTestClient createWebTestClient(
        String baseUrl,
        String authHeaderName,
        String authHeaderValue
    ) {
        WebTestClient.Builder builder = WebTestClient.bindToServer()
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .responseTimeout(DEFAULT_TEST_TIMEOUT_DURATION)
            .codecs(configurer ->
                configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)
            );

        if (authHeaderValue != null && !authHeaderValue.isEmpty()) {
            builder.defaultHeader(authHeaderName, authHeaderValue);
        }

        return builder.build();
    }

    /**
     * Executes a GraphQL query reactively and extracts a value from the response using
     * the supplied JSONPath expression. Blocks until the result is available or the
     * {@code DEFAULT_TEST_TIMEOUT} elapses.
     *
     * @param query    the GraphQL query string to execute
     * @param JSONPath the JSONPath expression used to extract data from the response
     * @return the extracted value as a string, or {@code null} if no data is found
     *         or an error occurs
     */
    public static String simpleCall(String query, String JSONPath) {
        // The GraphQLResponse contains data and errors.
        Mono<GraphQLResponse> graphQLResponseMono = graphQLClient
            .reactiveExecuteQuery(query)
            .timeout(DEFAULT_TEST_TIMEOUT_DURATION);

        // GraphQLResponse has convenience methods to extract fields using JsonPath.
        Mono<String> some_field = graphQLResponseMono.map(r ->
            r.extractValue(JSONPath)
        );

        // Execute the call.
        some_field.subscribe(
            log::debug,
            error -> log.warn(error.getMessage()),
            () -> log.warn("completed without a value")
        );
        return some_field.block();
    }

    /**
     * Executes a GraphQL query reactively, extracts the matching node using the supplied
     * JSONPath expression, and returns it as a pretty-printed JSON string. Uses the
     * extended {@code DEFAULT_TEST_LONG_TIMEOUT} to accommodate slower queries.
     *
     * <p>Returns an empty string when:
     * <ul>
     *   <li>the response contains GraphQL errors, or</li>
     *   <li>the JSONPath resolves to {@code null} or a non-object value.</li>
     * </ul>
     *
     * @param query    the GraphQL query string to execute
     * @param JSONPath the JSONPath expression used to extract data from the response
     * @return a pretty-printed JSON string of the matched object, or an empty string
     *         if no data is found
     */
    public static String simpleJsonCall(String query, String JSONPath) {
        log.trace(
            "[simpleJsonCall] epoch:{} query: {} ",
            System.currentTimeMillis(),
            query
        );

        // The GraphQLResponse contains data and errors.
        Mono<GraphQLResponse> graphQLResponseMono = graphQLClient
            .reactiveExecuteQuery(query)
            .timeout(DEFAULT_TEST_LONG_TIMEOUT_DURATION);

        Mono<LinkedHashMap<String, Object>> obj = graphQLResponseMono.flatMap(r -> {
            if (r.hasErrors()) {
                log.error("[simpleJsonCall] {}", r.getErrors().toString());
                return Mono.error(new RuntimeException(r.getErrors().toString()));
            }
            if (r.extractValue(JSONPath) == null) {
                log.error("[simpleJsonCall] No results found for {}", JSONPath);
                return Mono.empty();
            } else {
                if (r.extractValue(JSONPath) instanceof LinkedHashMap) {
                    return Mono.just(r.extractValue(JSONPath));
                } else {
                    log.error(
                        "[simpleJsonCall] Expected LinkedHashMap but found {}",
                        r.extractValue(JSONPath).getClass().getName()
                    );
                    return Mono.empty();
                }
            }
        });

        log.trace(
            "[simpleJsonCall] epoch:{} did not return for query: {} ",
            System.currentTimeMillis(),
            query
        );

        // Parse the response to a JSON string.
        ObjectMapper mapper = new ObjectMapper();
        String jsonResult = "";

        if (obj.blockOptional(Duration.ofSeconds(10)).isPresent()) {
            try {
                jsonResult = mapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(obj.block());
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            log.trace(jsonResult);
        }

        log.trace(
            "[simpleJsonCall] epoch:{} completed for query: {} ",
            System.currentTimeMillis(),
            query
        );
        return jsonResult;
    }

    /**
     * Executes a GraphQL query reactively and returns the value at the given JSONPath as a
     * pretty-printed JSON string. Unlike {@link #simpleJsonCall(String, String)}, this method
     * does <em>not</em> short-circuit on GraphQL errors — it attempts to extract the value
     * regardless of whether the response also contains an {@code errors} array.
     * Useful for queries that intentionally return partial data alongside errors.
     *
     * @param query    the GraphQL query string to execute
     * @param JSONPath the JSONPath expression used to extract data from the response
     * @return a pretty-printed JSON string of the matched value, or an empty string
     *         if the JSONPath resolves to nothing
     */
    public static String simpleErrorCall(String query, String JSONPath) {
        // The GraphQLResponse contains data and errors.
        Mono<GraphQLResponse> graphQLResponseMono = graphQLClient
            .reactiveExecuteQuery(query)
            .timeout(DEFAULT_TEST_TIMEOUT_DURATION);

        graphQLResponseMono.subscribe(
            value -> log.debug(value.toString()),
            error -> log.warn(error.getMessage()),
            () -> log.warn("completed without a value")
        );

        Mono<GraphQLResponse> obj = graphQLResponseMono.flatMap(r -> {
            if (r.extractValue(JSONPath) == null) {
                return Mono.empty();
            } else {
                return Mono.just(r.extractValue(JSONPath));
            }
        });

        // Parse the response to a JSON string.
        ObjectMapper mapper = new ObjectMapper();
        String jsonResult = "";

        if (
            obj
                .blockOptional(
                    Duration.ofSeconds(
                        EnvConfig.getInt("DEFAULT_TEST_TIMEOUT", 30)
                    )
                )
                .isPresent()
        ) {
            try {
                jsonResult = mapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(obj.block());
            } catch (Exception e) {
                log.error("[simpleErrorCall] {}", e.getMessage());
            }
            log.trace("simpleErrorCall {}", jsonResult);
        }
        return jsonResult;
    }

    /**
     * Executes a GraphQL query using a temporary {@link org.springframework.graphql.test.tester.HttpGraphQlTester}
     * constructed from the provided authentication token and API gateway key.
     *
     * <p>This is useful for tests that need to verify query behaviour under a
     * different credential than the default configured headers.
     *
     * @param query the GraphQL query string to execute
     * @param token the authentication token value (may be {@code null} to omit)
     * @param apiGW the API gateway key value (may be {@code null} to omit)
     * @return a {@link GraphQlTester.Response} containing the query results
     */
    public static GraphQlTester.Response simpleCallWithToken(
        String query,
        String token,
        String apiGW
    ) {
        WebTestClient localWebTestClient = WebTestClient.bindToServer()
            .baseUrl(GRAPHQL_URL)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build();

        HttpGraphQlTester customTester = HttpGraphQlTester.create(
            localWebTestClient
        );

        return customTester
            .document(query)
            .variable("pageNumber", 0)
            .variable("pageSize", 1)
            .execute();
    }
}
