/**
 * Reusable jqwik {@link net.jqwik.api.Arbitrary} generators for GraphQL
 * property-based testing.
 *
 * <p>This package supplies three complementary factories:
 * <ul>
 *   <li>{@link com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLArbitraries} —
 *       domain-agnostic generators for every standard GraphQL scalar type ({@code ID},
 *       {@code String}, {@code Int}, {@code Float}, {@code Boolean}), common patterns
 *       (nullable wrappers, UUID, numeric and alphanumeric IDs), temporal formats
 *       (ISO-8601 timestamps, dates, datetimes), Relay-cursor pagination, web values
 *       (email, URL, IPv4), and collection helpers.</li>
 *   <li>{@link com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLFuzzingArbitraries} —
 *       security and edge-case generators designed to surface vulnerabilities and
 *       robustness issues in GraphQL resolvers: SQL, NoSQL, XSS, path-traversal and
 *       command-injection payloads; extreme string lengths; special Unicode characters;
 *       numeric boundary values; malformed query syntax; and batch / alias-multiplication
 *       attack patterns.</li>
 *   <li>{@link com.snowedunderproductions.graphprobe.jqwik.arbitraries.DatabaseArbitraryProvider} —
 *       bridges the shared
 *       {@link com.snowedunderproductions.graphprobe.database.DatabaseConnectionManager}
 *       connection pool into jqwik, executing parameterised SQL queries and exposing each
 *       result row as an element of a typed {@link net.jqwik.api.Arbitrary}.  Useful when
 *       property tests must operate over values that actually exist in the database rather
 *       than purely generated ones.</li>
 * </ul>
 *
 * <h3>How the pieces fit together</h3>
 * <p>Arbitraries produced here are consumed in two ways:
 * <ul>
 *   <li>Directly, inside jqwik {@code @Provide} methods annotated with
 *       {@link com.snowedunderproductions.graphprobe.jqwik.annotations.GraphQLProperty}.</li>
 *   <li>Via the {@link com.snowedunderproductions.graphprobe.jqwik.providers.JqwikArgumentsProvider}
 *       bridge, which makes jqwik-generated values available to the standard
 *       {@link com.snowedunderproductions.graphprobe.annotations.DynamicSource} parameterised
 *       test mechanism.</li>
 * </ul>
 *
 * <h3>Typical usage — scalar generation</h3>
 * <pre>{@code
 * @GraphQLProperty
 * void testUserLookupWithGeneratedId(@ForAll("userIds") String userId) {
 *     // Test the GraphQL endpoint with a wide range of ID formats
 * }
 *
 * @Provide
 * Arbitrary<String> userIds() {
 *     return GraphQLArbitraries.graphqlId();
 * }
 * }</pre>
 *
 * <h3>Typical usage — security fuzzing</h3>
 * <pre>{@code
 * @GraphQLProperty(tries = 50)
 * void testRejectsInjectionPayloads(@ForAll("attacks") String payload) {
 *     // Resolver must reject or sanitise every payload without throwing 500
 * }
 *
 * @Provide
 * Arbitrary<String> attacks() {
 *     return GraphQLFuzzingArbitraries.injectionPayloads();
 * }
 * }</pre>
 *
 * <h3>Typical usage — database-sourced values</h3>
 * <pre>{@code
 * @GraphQLProperty
 * void testWithLiveData(@ForAll("activeUsers") String userId) {
 *     // Property test using IDs that genuinely exist in the database
 * }
 *
 * @Provide
 * Arbitrary<String> activeUsers() {
 *     return DatabaseArbitraryProvider
 *         .fromQuery("SELECT user_id FROM users WHERE active = true LIMIT 100")
 *         .extractString("user_id");
 * }
 * }</pre>
 *
 * @see com.snowedunderproductions.graphprobe.jqwik.annotations.GraphQLProperty
 * @see com.snowedunderproductions.graphprobe.jqwik.providers.JqwikArgumentsProvider
 * @see com.snowedunderproductions.graphprobe.database.DatabaseConnectionManager
 * @see com.snowedunderproductions.graphprobe.annotations.DynamicSource
 */
package com.snowedunderproductions.graphprobe.jqwik.arbitraries;
