package com.snowedunderproductions.graphprobe.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.snowedunderproductions.graphprobe.config.EnvConfig;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resilient OkHttp-based GraphQL client for integration testing.
 *
 * <p>Layered on top of {@link GraphQLTestClient} concepts but adds production-grade
 * resilience for noisy or saturated environments:
 * <ul>
 *   <li>A process-wide concurrency cap via {@link GraphQLRequestThrottle}.</li>
 *   <li>A single retry with jittered backoff for transient connect/read timeouts
 *       and HTTP 502/503/504 responses (read-only operations only).</li>
 *   <li>Runtime metrics published through {@link GraphQLRequestMetrics}.</li>
 *   <li>Allure attachments for the query, response and any error body.</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <p>All configuration is environment-driven (no hardcoded URLs), matching the
 * conventions of {@link GraphQLTestClient}:
 * <ul>
 *   <li>{@code GRAPHQL_URL} — the GraphQL endpoint URL (required)</li>
 *   <li>{@code AUTH_HEADER_NAME} — authorization header name (default {@code "Authorization"})</li>
 *   <li>{@code AUTH_HEADER_VALUE} — authorization header value (optional)</li>
 *   <li>{@code GRAPHQL_CONNECT_TIMEOUT_SECONDS} — connect timeout (default {@value #DEFAULT_TIMEOUT_SECONDS})</li>
 *   <li>{@code GRAPHQL_READ_TIMEOUT_SECONDS} — read timeout (default {@value #DEFAULT_TIMEOUT_SECONDS})</li>
 *   <li>{@code GRAPHQL_WRITE_TIMEOUT_SECONDS} — write timeout (default {@value #DEFAULT_TIMEOUT_SECONDS})</li>
 * </ul>
 *
 * @author Paul Snow
 * @since 0.0.0
 */
public class SimpleGraphQLClient {

	public static final MediaType JSON = MediaType.Companion.parse("application/json; charset=utf-8");

	/** Default timeout (seconds) for connect/read/write when no override is supplied. */
	public static final int DEFAULT_TIMEOUT_SECONDS = 45;

	private static final Logger log = LoggerFactory.getLogger(SimpleGraphQLClient.class);

	private final OkHttpClient client;
	private final ObjectMapper objectMapper;
	private final String graphqlUrl;
	private final String authHeaderName;
	private final String authHeaderValue;

	/**
	 * Create a new SimpleGraphQLClient with configuration from environment variables.
	 */
	public SimpleGraphQLClient() {
		this(EnvConfig.getRequired("GRAPHQL_URL"), EnvConfig.get("AUTH_HEADER_NAME", "Authorization"), EnvConfig.get("AUTH_HEADER_VALUE", ""));
	}

	/**
	 * Create a new SimpleGraphQLClient with explicit endpoint and auth configuration.
	 * Timeouts are still sourced from the environment.
	 *
	 * @param graphqlUrl      the GraphQL endpoint URL
	 * @param authHeaderName  the authorization header name
	 * @param authHeaderValue the authorization header value (skipped when blank)
	 */
	public SimpleGraphQLClient(String graphqlUrl, String authHeaderName, String authHeaderValue) {
		this.objectMapper = new ObjectMapper();
		this.graphqlUrl = graphqlUrl;
		this.authHeaderName = authHeaderName;
		this.authHeaderValue = authHeaderValue;
		this.client = new OkHttpClient.Builder()
			.connectTimeout(EnvConfig.getInt("GRAPHQL_CONNECT_TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS), TimeUnit.SECONDS)
			.readTimeout(EnvConfig.getInt("GRAPHQL_READ_TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS), TimeUnit.SECONDS)
			.writeTimeout(EnvConfig.getInt("GRAPHQL_WRITE_TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS), TimeUnit.SECONDS)
			.build();
	}

	/**
	 * Execute a query that is expecting to get an error.
	 *
	 * @param query the query to execute
	 * @return the error message returned by the query
	 * @throws IOException if there is an error executing the query
	 */
	@Step("GraphQL error query")
	public String executeErrorQuery(String query) throws IOException {
		return executeQuery(query, true, null);
	}

	/**
	 * Execute a query and return the data.
	 *
	 * @param query    the query to execute
	 * @param dataPath the path to the data in the response, eg {@code "$.data.allocationHistory[0]"}
	 * @return the data returned by the query
	 * @throws IOException if there is an error executing the query
	 */
	@Step("GraphQL query -> {1}")
	public String executeQuery(String query, String dataPath) throws IOException {
		return executeQuery(query, false, dataPath);
	}

	/**
	 * Execute a GraphQL query with variables and return data at the supplied JSONPath.
	 *
	 * @param query     the query to execute
	 * @param variables variables object sent in the GraphQL request payload
	 * @param dataPath  the JSONPath to the data in the response
	 * @return the extracted value as a string, or {@code null} if the path resolves to nothing
	 * @throws IOException if there is an error executing the query
	 */
	@Step("GraphQL query with variables -> {2}")
	public String executeQuery(String query, Map<String, ?> variables, String dataPath) throws IOException {
		return executeQuery(query, variables, null, false, dataPath, Map.of());
	}

	/**
	 * Execute a query and extract a single value from the JSON response at the supplied
	 * JSONPath. A convenience alias for {@link #executeQuery(String, String)} so callers
	 * (e.g. pagination helpers) can express intent clearly.
	 *
	 * @param query    the query to execute
	 * @param dataPath the JSONPath to the data in the response, eg {@code "$.data.allocated[0]"}
	 * @return the extracted value as a string, or {@code null} if the path resolves to nothing
	 * @throws IOException if there is an error executing the query
	 */
	@Step("GraphQL simple json call -> {1}")
	public String simpleJsonCall(String query, String dataPath) throws IOException {
		return executeQuery(query, false, dataPath);
	}

	/**
	 * Execute a query and return the data or error.
	 *
	 * @param query       the query to execute
	 * @param returnError if true, return the error message instead of throwing an exception
	 * @param dataPath    the path to the data in the response, if null is passed in it will become "$.data"
	 * @return the data or error message returned by the query
	 * @throws IOException if there is an error executing the query
	 */
	@Step("GraphQL query -> {2}")
	public String executeQuery(String query, boolean returnError, String dataPath) throws IOException {
		return executeQuery(query, Map.of(), null, returnError, dataPath, Map.of());
	}

	/**
	 * Execute a GraphQL operation with variables, optional operation name, and optional
	 * per-request headers.
	 *
	 * @param query         the query to execute
	 * @param variables     variables object sent in the GraphQL request payload
	 * @param operationName operation name to execute when the document contains named operations
	 * @param returnError   if true, return the error message instead of throwing an exception
	 * @param dataPath      the path to the data in the response, if null is passed in it will become "$.data"
	 * @param headers       additional request headers for this call
	 * @return the data or error message returned by the query
	 * @throws IOException if there is an error executing the query
	 */
	@Step("GraphQL operation -> {4}")
	public String executeQuery(String query, Map<String, ?> variables, String operationName, boolean returnError, String dataPath, Map<String, String> headers) throws IOException {
		log.trace("[executeQuery] query: {}", query);
		query = query.replace("\n", " ").replace("\r", " ");
		attach("GraphQL query", "application/graphql", query, ".graphql");

		String jsonString = requestBodyJson(query, variables, operationName);

		log.trace("[executeQuery] jsonString: {}", jsonString);
		RequestBody body = RequestBody.Companion.create(jsonString, JSON);

		Request request = buildRequest(body, headers);
		log.trace("[executeQuery] request: {}", request.toString());
		try (ThrottledResponse throttled = executeThrottledWithRetry(request, query); Response response = throttled.response()) {
			if (!response.isSuccessful()) {
				String errorBody = response.body().string();
				log.error("[executeQuery] error response: {}", errorBody);
				attach("GraphQL error response", "application/json", errorBody, ".json");
				throw new IOException("Unexpected code " + response);
			}
			String responseBodyString = response.body().string();

			log.debug("[executeQuery] success response: {}", responseBodyString);
			attach("GraphQL response", "application/json", responseBodyString, ".json");

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
				data = ctx.read(((null == dataPath || dataPath.isEmpty()) ? "$.data" : dataPath)).toString();
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
	 * Execute a query that may return either data or a specific error.
	 *
	 * <p>This function allows you to pass in a query, a dataPath and an optional error
	 * message to check for. It returns a {@link TestResponse} whose {@code success} flag
	 * is {@code true} if the expected error message is found, or if the data is found
	 * with no errors; {@code false} if the expected error is not found but a different
	 * error is present.
	 *
	 * @param query         the query to execute
	 * @param dataPath      the path to the data in the response, eg {@code "$.data.allocationHistory[0]"}
	 * @param expectedError the error message to check for eg {@code "must contain"}
	 * @return a {@link TestResponse} with success, data, and error fields populated
	 * @throws IOException if there is an error executing the query
	 */
	@Step("GraphQL query -> data or expected error")
	public TestResponse executeQueryOrPossibleSpecificError(@NotNull String query, Optional<String> dataPath, Optional<String> expectedError) throws IOException {
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
				return new TestResponse(false, data, error);
			}
			if (null != data) {
				log.debug("[4] TestResponse={} data={} error={}", true, data, error);
				return new TestResponse(true, data, error);
			}
			if (null != error) {
				log.debug("[5] TestResponse={} data={} error={}", true, data, error);
				return new TestResponse(error.contains(expectedError.get()), data, error);
			}
			// Neither data nor error found — query succeeded with empty results
			log.debug("[5b] TestResponse={} data={} error={}", true, data, error);
			return new TestResponse(true, data, error);
		}
		// Check if we are only looking for data
		if (dataPath.isPresent()) {
			log.debug("[6] TestResponse={} data={} error={}", null != data, data, error);
			return new TestResponse(null != data, data, error);
		}
		// check if we are only looking for errors
		if (expectedError.isPresent()) {
			log.debug("[7] TestResponse={} data={} error={}", null != error && error.contains(expectedError.get()), data, error);
			return new TestResponse(null != error && error.contains(expectedError.get()), data, error);
		}
		// if we get here it must be a fail.
		log.debug("[8] END TestResponse={} data={} error={}", false, data, error);
		return new TestResponse(false, data, error);
	}

	/**
	 * Executes a GraphQL query and asserts that all expected values are found somewhere
	 * in the response payload. This method is type-agnostic — it works with any response
	 * structure (objects, arrays, nested data) by searching the full JSON string.
	 * <p>
	 * Fails the test if:
	 * <ul>
	 *   <li>The GraphQL response contains errors</li>
	 *   <li>Any of the expected values are not found in the response</li>
	 * </ul>
	 *
	 * @param query          the GraphQL query to execute
	 * @param expectedValues values that must appear somewhere in the response JSON
	 * @return the full response string for further assertions
	 * @throws IOException    if there is an error executing the query
	 * @throws AssertionError if the GraphQL response contains errors or any expected value is missing
	 */
	@Step("GraphQL query -> assert response contains values")
	public String assertContains(@NotNull String query, String... expectedValues) throws IOException {
		String response = executeFullQuery(query);
		log.debug("[assertContains] response: {}", response);

		// Check for GraphQL errors
		try {
			String error = JsonPath.read(response, "$.errors[0].message");
			log.error("[assertContains] GraphQL error: {}", error);
			throw new AssertionError("GraphQL error in response: " + error);
		} catch (com.jayway.jsonpath.PathNotFoundException e) {
			log.trace("[assertContains] No GraphQL errors found");
		}

		for (String expected : expectedValues) {
			assertThat(response).as("Expected value '%s' not found in response", expected).contains(expected);
		}
		return response;
	}

	/**
	 * Executes a GraphQL query with two-phase validation: first <em>assumes</em> that data
	 * is present, then <em>asserts</em> that all expected values exist in the response.
	 * This method is type-agnostic — it works with any response structure (objects, arrays,
	 * nested data) by searching the full JSON string.
	 * <p>
	 * <strong>Phase 1 — Assume data exists (skip if not):</strong>
	 * <ul>
	 *   <li>If the GraphQL response contains errors → test is <em>skipped</em></li>
	 *   <li>If the {@code $.data} node is null or empty → test is <em>skipped</em></li>
	 * </ul>
	 * <strong>Phase 2 — Assert values match (fail if not):</strong>
	 * <ul>
	 *   <li>If data is present but any expected value is missing → test <em>fails</em></li>
	 * </ul>
	 *
	 * @param query          the GraphQL query to execute
	 * @param expectedValues values that must appear somewhere in the response JSON
	 * @return the full response string for further assertions
	 * @throws IOException if there is an error executing the query
	 */
	@Step("GraphQL query -> assume data, assert values")
	public String assumeDataAssertValues(@NotNull String query, String... expectedValues) throws IOException {
		String response = executeFullQuery(query);
		log.debug("[assumeDataAssertValues] response: {}", response);

		// Skip test if response has GraphQL errors (data may not be available in this environment)
		try {
			String error = JsonPath.read(response, "$.errors[0].message");
			log.info("[assumeDataAssertValues] GraphQL error (skipping test): {}", error);
			assumeThat(error).as("Skipping test due to GraphQL error: %s", error).isNull();
		} catch (com.jayway.jsonpath.PathNotFoundException e) {
			log.trace("[assumeDataAssertValues] No GraphQL errors found");
		}

		// Skip test if no data is present
		String data;
		try {
			data = JsonPath.read(response, "$.data").toString();
		} catch (com.jayway.jsonpath.PathNotFoundException e) {
			data = null;
		}
		assumeThat(data).as("Skipping test: no data in response").isNotNull().isNotEmpty();

		for (String expected : expectedValues) {
			assertThat(response).as("Expected value '%s' not found in response", expected).contains(expected);
		}
		return response;
	}

	/**
	 * Execute a query and return the full, unparsed response body.
	 *
	 * @param query the query to execute
	 * @return the full response body as a string
	 * @throws IOException if there is an error executing the query
	 */
	@Step("GraphQL full query")
	public String executeFullQuery(String query) throws IOException {
		return executeFullQuery(query, Map.of(), null, Map.of());
	}

	/**
	 * Execute a GraphQL operation with variables and return the full, unparsed response body.
	 *
	 * @param query         the query to execute
	 * @param variables     variables object sent in the GraphQL request payload
	 * @param operationName operation name to execute when the document contains named operations
	 * @return the full response body as a string
	 * @throws IOException if there is an error executing the query
	 */
	@Step("GraphQL full query with variables")
	public String executeFullQuery(String query, Map<String, ?> variables, String operationName) throws IOException {
		return executeFullQuery(query, variables, operationName, Map.of());
	}

	/**
	 * Execute a GraphQL operation with variables, optional operation name, and optional
	 * per-request headers, returning the full, unparsed response body.
	 *
	 * @param query         the query to execute
	 * @param variables     variables object sent in the GraphQL request payload
	 * @param operationName operation name to execute when the document contains named operations
	 * @param headers       additional request headers for this call
	 * @return the full response body as a string
	 * @throws IOException if there is an error executing the query
	 */
	@Step("GraphQL full operation")
	public String executeFullQuery(String query, Map<String, ?> variables, String operationName, Map<String, String> headers) throws IOException {
		log.trace("[executeFullQuery] query: {}", query);
		query = query.replace("\n", " ").replace("\r", " ");
		attach("GraphQL query", "application/graphql", query, ".graphql");

		String jsonString = requestBodyJson(query, variables, operationName);

		log.trace("[executeFullQuery] jsonString: {}", jsonString);
		RequestBody body = RequestBody.Companion.create(jsonString, JSON);

		Request request = buildRequest(body, headers);
		log.trace("[executeFullQuery] request: {}", request.toString());
		try (ThrottledResponse throttled = executeThrottledWithRetry(request, query); Response response = throttled.response()) {
			if (!response.isSuccessful()) {
				String errorBody = response.body().string();
				log.error("[executeFullQuery] error response: {}", errorBody);
				attach("GraphQL error response", "application/json", errorBody, ".json");
				throw new IOException("Unexpected code " + response);
			}
			String responseBodyString = response.body().string();

			log.trace("[executeFullQuery] success response: {}", responseBodyString);
			attach("GraphQL response", "application/json", responseBodyString, ".json");

			return responseBodyString;
		} catch (IOException e) {
			log.error("[executeFullQuery] error: {}", e.getMessage());
			throw e;
		}
	}

	/**
	 * Builds the POST request to the configured endpoint, adding the auth header only
	 * when an auth value is configured.
	 */
	private String requestBodyJson(String query, Map<String, ?> variables, String operationName) throws IOException {
		Map<String, Object> jsonMap = new HashMap<>();
		jsonMap.put("query", query);
		if (variables != null && !variables.isEmpty()) {
			jsonMap.put("variables", variables);
		}
		if (operationName != null && !operationName.isBlank()) {
			jsonMap.put("operationName", operationName);
		}
		return objectMapper.writeValueAsString(jsonMap);
	}

	private Request buildRequest(RequestBody body) {
		return buildRequest(body, Map.of());
	}

	/**
	 * Builds the POST request to the configured endpoint, adding the default auth header and
	 * any per-request headers when values are configured.
	 */
	private Request buildRequest(RequestBody body, Map<String, String> headers) {
		log.trace("[buildRequest] url: {}", graphqlUrl);
		Request.Builder builder = new Request.Builder().url(graphqlUrl).post(body);
		if (authHeaderValue != null && !authHeaderValue.isEmpty()) {
			builder.addHeader(authHeaderName, authHeaderValue);
		}
		if (headers != null) {
			headers.forEach((name, value) -> {
				if (name != null && !name.isBlank() && value != null) {
					builder.header(name, value);
				}
			});
		}
		return builder.build();
	}

	/**
	 * Executes the HTTP request with two layers of resilience: a process-wide
	 * concurrency cap via {@link GraphQLRequestThrottle}, and a single retry with
	 * jittered backoff for transient connect/read timeouts and HTTP 502/503/504
	 * responses. Mutations and subscriptions are NOT retried because the server
	 * may have partially applied them.
	 *
	 * <p>The returned {@link ThrottledResponse} owns the throttle permit and the
	 * underlying response; closing it releases the permit and closes the response.
	 */
	private ThrottledResponse executeThrottledWithRetry(Request request, String query) throws IOException {
		boolean acquired;
		try {
			acquired = GraphQLRequestThrottle.acquire();
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted waiting for GraphQL request permit", ie);
		}
		if (!acquired) {
			throw new IOException("Timed out waiting for GraphQL request permit (in-flight=" + (GraphQLRequestThrottle.configuredPermits() - GraphQLRequestThrottle.availablePermits()) + ")");
		}
		try {
			Response response = executeWithRetry(request, query);
			return new ThrottledResponse(response);
		} catch (RuntimeException | IOException e) {
			GraphQLRequestThrottle.release();
			throw e;
		}
	}

	/**
	 * Executes the HTTP request with up to one retry for idempotent (non-mutation,
	 * non-subscription) queries. Records call duration and success/failure via
	 * {@link GraphQLRequestMetrics#recordCall(long, boolean)}.
	 *
	 * @param request the OkHttp request to execute
	 * @param query   the original GraphQL query string, used to determine idempotency
	 * @return the HTTP {@link Response} (caller must close it)
	 * @throws IOException on network failure or after all retry attempts are exhausted
	 */
	private Response executeWithRetry(Request request, String query) throws IOException {
		boolean retriable = isIdempotent(query);
		int maxAttempts = retriable ? 2 : 1;
		IOException lastError = null;
		long startNanos = System.nanoTime();
		boolean success = false;
		try {
			for (int attempt = 1; attempt <= maxAttempts; attempt++) {
				Response response;
				try {
					response = client.newCall(request).execute();
				} catch (SocketTimeoutException | ConnectException e) {
					lastError = e;
					if (attempt < maxAttempts) {
						GraphQLRequestMetrics.recordRetry();
						log.warn("[executeWithRetry] retrying after {}: {} (attempt {}/{})", e.getClass().getSimpleName(), e.getMessage(), attempt, maxAttempts);
						sleepBackoff();
						continue;
					}
					throw e;
				}
				int code = response.code();
				if (retriable && (code == 502 || code == 503 || code == 504) && attempt < maxAttempts) {
					response.close();
					GraphQLRequestMetrics.recordRetry();
					log.warn("[executeWithRetry] retrying after HTTP {} (attempt {}/{})", code, attempt, maxAttempts);
					sleepBackoff();
					lastError = new IOException("Transient HTTP " + code);
					continue;
				}
				success = true;
				return response;
			}
			throw lastError != null ? lastError : new IOException("executeWithRetry exhausted attempts");
		} finally {
			GraphQLRequestMetrics.recordCall(System.nanoTime() - startNanos, success);
		}
	}

	/**
	 * Returns {@code true} when the query is a read-only operation that is safe to
	 * retry. Mutations and subscriptions are excluded because their server-side
	 * effects may be partially applied after a timeout.
	 *
	 * @param query the GraphQL query string to inspect
	 * @return {@code true} for queries; {@code false} for mutations, subscriptions,
	 *         or {@code null} input
	 */
	private static boolean isIdempotent(String query) {
		if (query == null) {
			return false;
		}
		String trimmed = query.trim().toLowerCase();
		return !trimmed.startsWith("mutation") && !trimmed.startsWith("subscription");
	}

	private static void sleepBackoff() {
		try {
			long jitterMs = 250L + ThreadLocalRandom.current().nextLong(500L);
			Thread.sleep(jitterMs);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Pairs an OkHttp {@link Response} with its throttle permit so both are released
	 * by a single try-with-resources block in the caller.
	 */
	private static final class ThrottledResponse implements AutoCloseable {

		private final Response response;
		private boolean closed;

		ThrottledResponse(Response response) {
			this.response = response;
		}

		Response response() {
			return response;
		}

		@Override
		public void close() {
			if (closed) {
				return;
			}
			closed = true;
			GraphQLRequestThrottle.release();
		}
	}

	/**
	 * Attaches content to the active Allure step/test, swallowing any failure so test
	 * behaviour is never affected by reporting. No-ops when {@code content} is blank.
	 *
	 * @param name      attachment name shown in the Allure report
	 * @param type      MIME type of the content
	 * @param content   the content to attach (skipped when {@code null} or empty)
	 * @param extension file extension used for the attachment
	 */
	private static void attach(String name, String type, @Nullable String content, String extension) {
		if (content == null || content.isEmpty()) {
			return;
		}
		try {
			Allure.addAttachment(name, type, content, extension);
		} catch (Exception e) {
			log.debug("[attach] could not attach '{}': {}", name, e.getMessage());
		}
	}
}
