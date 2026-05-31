/**
 * GraphQL test client utilities — HTTP and reactive clients, response wrappers,
 * concurrency throttling, per-request metrics with Allure, and pagination/deep-link
 * assertion helpers.
 *
 * <h3>Client implementations</h3>
 * <p>Two HTTP client implementations are provided, both environment-driven and
 * domain-agnostic:
 * <ul>
 *   <li>{@link com.snowedunderproductions.graphprobe.client.GraphQLTestClient} — a
 *       straightforward OkHttp client suited for tests that do not require retry
 *       logic or Allure attachments.</li>
 *   <li>{@link com.snowedunderproductions.graphprobe.client.SimpleGraphQLClient} — a
 *       resilient OkHttp client that adds a jittered single-retry for transient
 *       failures, process-wide concurrency throttling via
 *       {@link com.snowedunderproductions.graphprobe.client.GraphQLRequestThrottle},
 *       runtime metrics via
 *       {@link com.snowedunderproductions.graphprobe.client.GraphQLRequestMetrics},
 *       and Allure step/attachment integration.</li>
 *   <li>{@link com.snowedunderproductions.graphprobe.client.GraphQLTestExecutor} — a
 *       Spring WebFlux / Netflix DGS reactive executor that exposes pre-configured
 *       static instances of {@link org.springframework.test.web.reactive.server.WebTestClient}
 *       and {@link com.netflix.graphql.dgs.client.WebClientGraphQLClient} for tests
 *       that prefer the reactive programming model.</li>
 * </ul>
 *
 * <h3>Response handling</h3>
 * <p>{@link com.snowedunderproductions.graphprobe.client.TestResponse} is an immutable
 * record returned by the {@code executeQueryOrPossibleSpecificError} variants on both
 * HTTP clients. It captures the success flag, the extracted data string, and the
 * first GraphQL error message, with convenience predicates such as
 * {@link com.snowedunderproductions.graphprobe.client.TestResponse#dataOnlySuccess()},
 * {@link com.snowedunderproductions.graphprobe.client.TestResponse#containsError(String)},
 * and {@link com.snowedunderproductions.graphprobe.client.TestResponse#containsData(String)}.
 *
 * <h3>Concurrency and observability</h3>
 * <ul>
 *   <li>{@link com.snowedunderproductions.graphprobe.client.GraphQLRequestThrottle} —
 *       a process-wide semaphore that caps the number of simultaneous in-flight
 *       requests, preventing server saturation during aggressive parallel test
 *       execution. Sized via {@code GRAPHQL_MAX_CONCURRENT}.</li>
 *   <li>{@link com.snowedunderproductions.graphprobe.client.GraphQLRequestMetrics} —
 *       collects call counts, error/retry rates, permit-wait time, and request
 *       duration on both a global and a per-test-class basis. A periodic summary
 *       is logged at INFO, and the final summary is printed on JVM shutdown.</li>
 *   <li>{@link com.snowedunderproductions.graphprobe.client.GraphQLMetricsExtension} —
 *       a JUnit 5 {@link org.junit.jupiter.api.extension.AfterAllCallback} that
 *       attaches the per-class metrics summary to the Allure report after each test
 *       class completes.</li>
 * </ul>
 *
 * <h3>Assertion helpers</h3>
 * <ul>
 *   <li>{@link com.snowedunderproductions.graphprobe.client.PaginationTestHelper} —
 *       iterates through multiple pages of a paginated GraphQL query, asserting or
 *       assuming expected fields on each page. Page geometry defaults are
 *       environment-driven and clamped by
 *       {@link com.snowedunderproductions.graphprobe.config.TestProfile}.</li>
 *   <li>{@link com.snowedunderproductions.graphprobe.client.DeepLinkTestHelper} —
 *       validates nested and resolved fields in a GraphQL JSON response, supporting
 *       both strict ({@code assert*}) and lenient ({@code assume*}) flavours for
 *       data that may not be available in every environment.</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <p>All configuration is read from the environment via
 * {@link com.snowedunderproductions.graphprobe.config.EnvConfig}. No URLs, credentials,
 * or tuning parameters are hardcoded, keeping the framework domain-agnostic and safe
 * to share across projects. Key variables shared by the HTTP clients:
 * <ul>
 *   <li>{@code GRAPHQL_URL} — the GraphQL endpoint URL (required)</li>
 *   <li>{@code AUTH_HEADER_NAME} — authorisation header name (default {@code Authorization})</li>
 *   <li>{@code AUTH_HEADER_VALUE} — authorisation header value (optional)</li>
 * </ul>
 *
 * @see com.snowedunderproductions.graphprobe.config
 * @see com.snowedunderproductions.graphprobe.database
 * @see com.snowedunderproductions.graphprobe.annotations
 */
package com.snowedunderproductions.graphprobe.client;
