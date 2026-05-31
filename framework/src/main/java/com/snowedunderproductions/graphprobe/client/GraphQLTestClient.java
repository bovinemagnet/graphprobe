package com.snowedunderproductions.graphprobe.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.snowedunderproductions.graphprobe.config.EnvConfig;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Straightforward OkHttp-based GraphQL client for integration testing.
 *
 * <p>Sends GraphQL queries to a configured endpoint and provides convenient
 * methods for handling successful responses, expected-error responses, and
 * cases where either a data result or a named error is acceptable.
 * All responses are parsed with JsonPath.
 *
 * <p>For tests that also require retry logic, concurrency throttling, or
 * Allure attachments, prefer {@link SimpleGraphQLClient}.
 *
 * <h3>Configuration</h3>
 * <p>All values are read from the environment via
 * {@link com.snowedunderproductions.graphprobe.config.EnvConfig}:
 * <ul>
 *   <li>{@code GRAPHQL_URL} — the GraphQL endpoint URL (required)</li>
 *   <li>{@code AUTH_HEADER_NAME} — authorisation header name
 *       (default: {@code "Authorization"})</li>
 *   <li>{@code AUTH_HEADER_VALUE} — authorisation header value (optional;
 *       header is omitted when blank)</li>
 * </ul>
 *
 * <h3>Usage example</h3>
 * <pre>{@code
 * GraphQLTestClient client = new GraphQLTestClient();
 *
 * // Execute a query and extract a value by JSONPath
 * String data = client.executeQuery(query, "$.data.users");
 *
 * // Execute a query that is expected to return a GraphQL error
 * String error = client.executeErrorQuery(query);
 *
 * // Execute a query that may return data or a specific error
 * TestResponse response = client.executeQueryOrPossibleSpecificError(
 *     query,
 *     Optional.of("$.data.user"),
 *     Optional.of("User not found")
 * );
 * }</pre>
 *
 * @see SimpleGraphQLClient
 * @see TestResponse
 */
public class GraphQLTestClient {

    public static final MediaType JSON = MediaType.Companion.parse(
        "application/json; charset=utf-8"
    );

    private static final Logger log = LoggerFactory.getLogger(
        GraphQLTestClient.class
    );

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper;
    private final String graphqlUrl;
    private final String authHeaderName;
    private final String authHeaderValue;

    /**
     * Creates a new {@code GraphQLTestClient} reading all configuration from environment
     * variables via {@link com.snowedunderproductions.graphprobe.config.EnvConfig}.
     * Throws {@link IllegalStateException} if {@code GRAPHQL_URL} is not set.
     */
    public GraphQLTestClient() {
        this.objectMapper = new ObjectMapper();
        this.graphqlUrl = EnvConfig.getRequired("GRAPHQL_URL");
        this.authHeaderName = EnvConfig.get("AUTH_HEADER_NAME", "Authorization");
        this.authHeaderValue = EnvConfig.get("AUTH_HEADER_VALUE", "");
    }

    /**
     * Creates a new {@code GraphQLTestClient} with explicit endpoint and authorisation
     * configuration, bypassing environment variable lookup.
     *
     * @param graphqlUrl      the GraphQL endpoint URL
     * @param authHeaderName  the authorisation header name
     * @param authHeaderValue the authorisation header value (header is omitted when blank)
     */
    public GraphQLTestClient(
        String graphqlUrl,
        String authHeaderName,
        String authHeaderValue
    ) {
        this.objectMapper = new ObjectMapper();
        this.graphqlUrl = graphqlUrl;
        this.authHeaderName = authHeaderName;
        this.authHeaderValue = authHeaderValue;
    }

    /**
     * Executes a GraphQL query that is expected to return an error.
     *
     * @param query the GraphQL query string to execute
     * @return the first GraphQL error message, or {@code null} if no error was present
     * @throws IOException if the HTTP exchange fails
     * @see #executeQuery(String, boolean, String)
     */
    public String executeErrorQuery(String query) throws IOException {
        return executeQuery(query, true, null);
    }

    /**
     * Executes a GraphQL query and returns the value found at the given JSONPath.
     * Throws {@link IOException} if the response contains a GraphQL error.
     *
     * @param query    the GraphQL query string to execute
     * @param dataPath the JSONPath expression for the desired value,
     *                 e.g. {@code "$.data.allocationHistory[0]"}
     * @return the extracted value as a string, or {@code null} if the path resolves to nothing
     * @throws IOException if the HTTP exchange fails or the response contains an error
     */
    public String executeQuery(String query, String dataPath)
        throws IOException {
        return executeQuery(query, false, dataPath);
    }

    /**
     * Executes a GraphQL query and returns either data or the first error message,
     * depending on the {@code returnError} flag.
     *
     * <p>When {@code returnError} is {@code true} the method returns the first GraphQL
     * error message (or {@code null} if none is present) and never throws for
     * application-level errors. When {@code false}, an error in the response causes
     * an {@link IOException} to be thrown.
     *
     * @param query       the GraphQL query string to execute
     * @param returnError {@code true} to return the error message rather than throwing
     * @param dataPath    the JSONPath to the desired value; defaults to {@code "$.data"}
     *                    when {@code null} or empty
     * @return the extracted data string, or the first error message when
     *         {@code returnError} is {@code true}
     * @throws IOException if the HTTP exchange fails, or if an error is present in the
     *                     response and {@code returnError} is {@code false}
     */
    public String executeQuery(String query, boolean returnError, String dataPath)
        throws IOException {
        log.trace("[executeQuery] query: {}", query);
        query = query.replace("\n", " ").replace("\r", " ");

        Map<String, String> jsonMap = new HashMap<>();
        jsonMap.put("query", query);
        String jsonString = objectMapper.writeValueAsString(jsonMap);

        log.trace("[executeQuery] jsonString: {}", jsonString);
        RequestBody body = RequestBody.Companion.create(jsonString, JSON);

        log.trace("[executeQuery] url: {}", graphqlUrl);
        Request.Builder requestBuilder = new Request.Builder()
            .url(graphqlUrl)
            .post(body);

        // Add auth header if configured
        if (authHeaderValue != null && !authHeaderValue.isEmpty()) {
            requestBuilder.addHeader(authHeaderName, authHeaderValue);
        }

        Request request = requestBuilder.build();
        log.trace("[executeQuery] request: {}", request.toString());

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error(
                    "[executeQuery] error response: {}",
                    response.body().string()
                );
                throw new IOException("Unexpected code " + response);
            }
            String responseBodyString = response.body().string();

            log.debug("[executeQuery] success response: {}", responseBodyString);

            log.trace("Parsing json body");
            ReadContext ctx = JsonPath.parse(responseBodyString);
            log.trace("Looking for error in {}", ctx.jsonString());
            String error;
            try {
                error = ctx.read("$.errors[0].message");
                log.debug("Errors Found={}", error);
            } catch (Exception e) {
                log.info("EX: No errors found msg:{}", e.getMessage());
                error = null;
            }
            String data;
            log.debug("Looking for data in {}", ctx.jsonString());
            try {
                data = ctx
                    .read(
                        (null == dataPath || dataPath.isEmpty())
                            ? "$.data"
                            : dataPath
                    )
                    .toString();
            } catch (Exception e) {
                log.info("EX: No data found msg:{}", e.getMessage());
                data = null;
            }

            log.trace("Data Found={}", data);
            if (returnError) {
                log.debug("[executeQuery] Error response path: {}", error);
                return error;
            } else {
                // check if there is an error
                if (error != null) {
                    log.error("[executeQuery] error response: {}", error);
                    throw new IOException("Error in response " + error);
                }
                log.debug("[executeQuery] Data response path: {}", data);
                return data;
            }
        }
    }

    /**
     * Executes a GraphQL query and returns the full, unparsed response body.
     * No JSONPath extraction or error inspection is performed; the raw JSON
     * string is returned as-is for callers that need full control over parsing.
     *
     * @param query the GraphQL query string to execute
     * @return the raw HTTP response body as a string
     * @throws IOException if the HTTP exchange fails or returns a non-2xx status
     */
    public String executeFullQuery(String query) throws IOException {
        log.trace("[executeFullQuery] query: {}", query);
        query = query.replace("\n", " ").replace("\r", " ");

        Map<String, String> jsonMap = new HashMap<>();
        jsonMap.put("query", query);
        String jsonString = objectMapper.writeValueAsString(jsonMap);

        log.trace("[executeFullQuery] jsonString: {}", jsonString);
        RequestBody body = RequestBody.Companion.create(jsonString, JSON);

        log.trace("[executeFullQuery] url: {}", graphqlUrl);
        Request.Builder requestBuilder = new Request.Builder()
            .url(graphqlUrl)
            .post(body);

        // Add auth header if configured
        if (authHeaderValue != null && !authHeaderValue.isEmpty()) {
            requestBuilder.addHeader(authHeaderName, authHeaderValue);
        }

        Request request = requestBuilder.build();
        log.trace("[executeFullQuery] request: {}", request.toString());

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error(
                    "[executeFullQuery] error response: {}",
                    response.body().string()
                );
                throw new IOException("Unexpected code " + response);
            }
            String responseBodyString = response.body().string();

            log.trace("[executeFullQuery] success response: {}", responseBodyString);

            return responseBodyString;
        } catch (IOException e) {
            log.error("[executeFullQuery] error: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Execute a query that may return either data or a specific error.
     *
     * <p>This function allows you to pass in a query, a dataPath and an optional error message to check for.
     * It will return a {@link TestResponse} with success=true if:
     * <ul>
     *   <li>The expected error message is found in the response</li>
     *   <li>The data is found and there were no errors</li>
     * </ul>
     *
     * <p>It will return success=false if:
     * <ul>
     *   <li>The expected error is not found, but a different error is there</li>
     *   <li>Neither data nor the expected error is found</li>
     * </ul>
     *
     * @param query the query to execute
     * @param dataPath the path to the data in the response, eg {@code "$.data.allocationHistory[0]"}
     * @param expectedError the error message to check for eg {@code "must contain"}
     * @return a {@link TestResponse} with success, data, and error fields populated
     * @throws IOException if there is an error executing the query
     */
    public TestResponse executeQueryOrPossibleSpecificError(
        @NotNull String query,
        Optional<String> dataPath,
        Optional<String> expectedError
    ) throws IOException {
        String response = executeFullQuery(query);
        String data = null;
        String error = null;

        // check for data if there is a datapath is provided
        if (dataPath.isPresent()) {
            try {
                data = JsonPath.read(response, dataPath.get()).toString();

                log.debug("[1] Data Found={}", data);
            } catch (Exception e) {
                log.debug("[1] EX: No data found msg:{}", e.getMessage());
                data = null;
            }
        }

        if (expectedError.isPresent()) {
            try {
                error = JsonPath.read(response, "$.errors[0].message").toString();
                log.debug("[2] Errors Found={}", error);
            } catch (Exception e) {
                log.debug("[2] EX: No errors found msg:{}", e.getMessage());
                error = null;
            }
        }

        // now check if we are looking for data or a specific error
        if (dataPath.isPresent() && expectedError.isPresent()) {
            if (null != data && null != error) {
                log.error("[3] Both data and error found, this is not expected");
                log.debug("[3] TestResponse={} data={} error={}", true, data, error);

                // Both data and error present is unexpected
                return new TestResponse(false, data, error);
            }
            if (null != data) {
                log.debug("[4] TestResponse={} data={} error={}", true, data, error);
                return new TestResponse(true, data, error);
            }
            if (null != error) {
                log.debug("[5] TestResponse={} data={} error={}", true, data, error);
                return new TestResponse(
                    error.contains(expectedError.get()),
                    data,
                    error
                );
            }
        }
        // Check if we are only looking for data
        if (dataPath.isPresent()) {
            log.debug("[6] TestResponse={} data={} error={}", null != data, data, error);
            return new TestResponse(null != data, data, error);
        }
        // check if we are only looking for errors
        if (expectedError.isPresent()) {
            log.debug(
                "[7] TestResponse={} data={} error={}",
                null != error && error.contains(expectedError.get()),
                data,
                error
            );
            return new TestResponse(
                null != error && error.contains(expectedError.get()),
                data,
                error
            );
        }
        // if we get here it must be a fail.
        log.debug("[8] END TestResponse={} data={} error={}", false, data, error);
        return new TestResponse(false, data, error);
    }
}
