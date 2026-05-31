package com.snowedunderproductions.graphprobe.jqwik.arbitraries;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.web.api.Web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Factory providing reusable, thread-safe {@link net.jqwik.api.Arbitrary} generators
 * for common GraphQL scalar types, structural patterns, and domain values.
 *
 * <p>All methods are static and the class is not instantiable.  Generators are designed
 * to be composed freely using jqwik's {@link net.jqwik.api.Combinators} API.
 *
 * <h3>Categories of generators</h3>
 * <ul>
 *   <li><strong>GraphQL scalars</strong> — {@link #graphqlId()}, {@link #graphqlString()},
 *       {@link #graphqlInt()}, {@link #graphqlFloat()}, {@link #graphqlBoolean()}</li>
 *   <li><strong>Common ID patterns</strong> — {@link #uuidString()}, {@link #numericId()},
 *       {@link #alphanumericId()}</li>
 *   <li><strong>Nullable wrappers</strong> — {@link #nullable(net.jqwik.api.Arbitrary)}</li>
 *   <li><strong>Temporal types</strong> — {@link #isoTimestamp()}, {@link #isoDate()},
 *       {@link #isoDateTime()}</li>
 *   <li><strong>Pagination / Relay</strong> — {@link #paginationLimit()},
 *       {@link #paginationOffset()}, {@link #relayCursor()}</li>
 *   <li><strong>Web / network</strong> — {@link #email()}, {@link #url()},
 *       {@link #ipv4()}</li>
 *   <li><strong>Collections</strong> — {@link #graphqlList(net.jqwik.api.Arbitrary)},
 *       {@link #nonEmptyGraphqlList(net.jqwik.api.Arbitrary)}</li>
 * </ul>
 *
 * <h3>Usage example</h3>
 * <pre>{@code
 * @GraphQLProperty
 * void testWithGraphQLId(@ForAll("graphqlIds") String id) {
 *     assertThat(id).isNotEmpty();
 * }
 *
 * @Provide
 * Arbitrary<String> graphqlIds() {
 *     return GraphQLArbitraries.graphqlId();
 * }
 * }</pre>
 *
 * @see GraphQLFuzzingArbitraries
 * @see DatabaseArbitraryProvider
 * @see com.snowedunderproductions.graphprobe.jqwik.annotations.GraphQLProperty
 * @see <a href="https://spec.graphql.org/October2021/#sec-Scalars">GraphQL Scalar Types specification</a>
 * @since 1.0.0
 */
public final class GraphQLArbitraries {

    private GraphQLArbitraries() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    // ==================== GraphQL Scalar Types ====================

    /**
     * Generates GraphQL ID scalars as strings.
     * <p>
     * IDs can be integers, UUIDs, or custom alphanumeric strings.
     * Distribution: an even (uniform) mix of numeric, UUID, and alphanumeric (via {@code Arbitraries.oneOf}).
     * </p>
     *
     * @return arbitrary for GraphQL ID type
     */
    public static Arbitrary<String> graphqlId() {
        return Arbitraries.oneOf(
            numericId(),
            uuidString(),
            alphanumericId()
        );
    }

    /**
     * Generates GraphQL String scalars.
     * <p>
     * Strings are GraphQL-safe: no control characters, properly escaped quotes.
     * Default length: 0-200 characters.
     * </p>
     *
     * @return arbitrary for GraphQL String type
     */
    public static Arbitrary<String> graphqlString() {
        return graphqlString(0, 200);
    }

    /**
     * Generates GraphQL String scalars with specified length constraints.
     *
     * @param minLength minimum string length (inclusive)
     * @param maxLength maximum string length (inclusive)
     * @return arbitrary for GraphQL String type
     */
    public static Arbitrary<String> graphqlString(int minLength, int maxLength) {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars(" .,!?-_'")
            .ofMinLength(minLength)
            .ofMaxLength(maxLength)
            .filter(s -> !s.contains("\n") && !s.contains("\r") && !s.contains("\t"));
    }

    /**
     * Generates GraphQL Int scalars (32-bit signed integers).
     *
     * @return arbitrary for GraphQL Int type
     */
    public static Arbitrary<Integer> graphqlInt() {
        return Arbitraries.integers();
    }

    /**
     * Generates GraphQL Int scalars within a specified range.
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (inclusive)
     * @return arbitrary for GraphQL Int type
     */
    public static Arbitrary<Integer> graphqlInt(int min, int max) {
        return Arbitraries.integers().between(min, max);
    }

    /**
     * Generates GraphQL Float scalars (double-precision floating point).
     *
     * @return arbitrary for GraphQL Float type
     */
    public static Arbitrary<Double> graphqlFloat() {
        return Arbitraries.doubles()
            .between(-1_000_000.0, 1_000_000.0)
            .ofScale(2);
    }

    /**
     * Generates GraphQL Float scalars within a specified range.
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (inclusive)
     * @param scale decimal places to round to
     * @return arbitrary for GraphQL Float type
     */
    public static Arbitrary<Double> graphqlFloat(double min, double max, int scale) {
        return Arbitraries.doubles()
            .between(min, max)
            .ofScale(scale);
    }

    /**
     * Generates GraphQL Boolean scalars.
     *
     * @return arbitrary for GraphQL Boolean type
     */
    public static Arbitrary<Boolean> graphqlBoolean() {
        return Arbitraries.of(true, false);
    }

    // ==================== Common GraphQL Patterns ====================

    /**
     * Generates nullable values (GraphQL nullable types).
     * <p>
     * Distribution: 80% non-null values, 20% null values.
     * </p>
     *
     * @param <T> the type of non-null values
     * @param nonNullArbitrary arbitrary for non-null values
     * @return arbitrary that may produce null
     */
    public static <T> Arbitrary<T> nullable(Arbitrary<T> nonNullArbitrary) {
        return nullable(nonNullArbitrary, 0.2);
    }

    /**
     * Generates nullable values with custom null probability.
     *
     * @param <T> the type of non-null values
     * @param nonNullArbitrary arbitrary for non-null values
     * @param nullProbability probability of null (0.0 to 1.0)
     * @return arbitrary that may produce null
     */
    public static <T> Arbitrary<T> nullable(Arbitrary<T> nonNullArbitrary, double nullProbability) {
        return nonNullArbitrary.injectNull(nullProbability);
    }

    /**
     * Generates UUID strings (common for GraphQL IDs).
     *
     * @return arbitrary producing UUID strings
     */
    public static Arbitrary<String> uuidString() {
        return Arbitraries.randomValue(random -> UUID.randomUUID().toString());
    }

    /**
     * Generates numeric ID strings (1 to 999999999).
     *
     * @return arbitrary producing numeric ID strings
     */
    public static Arbitrary<String> numericId() {
        return Arbitraries.longs()
            .between(1L, 999_999_999L)
            .map(String::valueOf);
    }

    /**
     * Generates alphanumeric ID strings (8-16 characters).
     *
     * @return arbitrary producing alphanumeric ID strings
     */
    public static Arbitrary<String> alphanumericId() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(8)
            .ofMaxLength(16);
    }

    // ==================== Temporal Types ====================

    /**
     * Generates ISO-8601 timestamp strings suitable for a GraphQL {@code DateTime} scalar.
     *
     * <p>The generator samples uniformly from two fixed boundary points computed at
     * arbitrary-creation time: exactly one year before {@code Instant.now()} and exactly
     * one year after it.  Use these values to exercise past and future timestamp handling
     * in resolvers.
     *
     * @return arbitrary producing ISO-8601 UTC timestamp strings (e.g. {@code 2024-05-31T12:00:00Z})
     */
    public static Arbitrary<String> isoTimestamp() {
        return Arbitraries.of(
            Instant.now().minusSeconds(365L * 24 * 60 * 60), // 1 year ago
            Instant.now().plusSeconds(365L * 24 * 60 * 60)   // 1 year from now
        ).map(Instant::toString);
    }

    /**
     * Generates ISO-8601 date strings ({@code YYYY-MM-DD}) suitable for a GraphQL
     * {@code Date} scalar.
     *
     * <p>The generator samples uniformly from two fixed boundary points computed at
     * arbitrary-creation time: exactly 365 days before {@code LocalDate.now()} and exactly
     * 365 days after it.
     *
     * @return arbitrary producing ISO date strings (e.g. {@code 2024-05-31})
     */
    public static Arbitrary<String> isoDate() {
        return Arbitraries.of(
            LocalDate.now().minusDays(365),
            LocalDate.now().plusDays(365)
        ).map(LocalDate::toString);
    }

    /**
     * Generates ISO-8601 local date-time strings suitable for a GraphQL
     * {@code DateTime} scalar that omits a timezone offset.
     *
     * <p>The generator samples uniformly from two fixed boundary points computed at
     * arbitrary-creation time: exactly 365 days before {@code LocalDateTime.now()} and
     * exactly 365 days after it.
     *
     * @return arbitrary producing ISO local date-time strings
     *         (e.g. {@code 2024-05-31T12:00:00})
     */
    public static Arbitrary<String> isoDateTime() {
        return Arbitraries.of(
            LocalDateTime.now().minusDays(365),
            LocalDateTime.now().plusDays(365)
        ).map(LocalDateTime::toString);
    }

    // ==================== Pagination & Relay Patterns ====================

    /**
     * Generates pagination limit values (1-100, biased towards common values).
     *
     * @return arbitrary for pagination limits
     */
    public static Arbitrary<Integer> paginationLimit() {
        return Arbitraries.oneOf(
            Arbitraries.just(10),
            Arbitraries.just(20),
            Arbitraries.just(50),
            Arbitraries.integers().between(1, 100)
        );
    }

    /**
     * Generates pagination offset values (0-1000).
     *
     * @return arbitrary for pagination offsets
     */
    public static Arbitrary<Integer> paginationOffset() {
        return Arbitraries.integers().between(0, 1000);
    }

    /**
     * Generates Relay-style cursor strings (base64-encoded).
     *
     * @return arbitrary producing cursor strings
     */
    public static Arbitrary<String> relayCursor() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofLength(10),
            Arbitraries.integers().between(0, 999999)
        ).as((prefix, num) ->
            java.util.Base64.getEncoder().encodeToString((prefix + ":" + num).getBytes())
        );
    }

    // ==================== Web & Network Types ====================

    /**
     * Generates valid email addresses.
     *
     * @return arbitrary producing email strings
     */
    public static Arbitrary<String> email() {
        return Web.emails();
    }

    /**
     * Generates valid URLs.
     *
     * @return arbitrary producing URL strings
     */
    public static Arbitrary<String> url() {
        return Web.webDomains().map(domain -> "https://" + domain);
    }

    /**
     * Generates IPv4 addresses.
     *
     * @return arbitrary producing IPv4 strings
     */
    public static Arbitrary<String> ipv4() {
        return Combinators.combine(
            Arbitraries.integers().between(0, 255),
            Arbitraries.integers().between(0, 255),
            Arbitraries.integers().between(0, 255),
            Arbitraries.integers().between(0, 255)
        ).as((a, b, c, d) -> String.format("%d.%d.%d.%d", a, b, c, d));
    }

    // ==================== Enum-like Patterns ====================

    /**
     * Generates values from a set of enum-like strings.
     *
     * @param values possible enum values
     * @return arbitrary choosing from provided values
     */
    @SafeVarargs
    public static <T> Arbitrary<T> enumValue(T... values) {
        return Arbitraries.of(values);
    }

    // ==================== Collection Patterns ====================

    /**
     * Generates lists of GraphQL items (0-10 elements by default).
     *
     * @param <T> the element type
     * @param elementArbitrary arbitrary for list elements
     * @return arbitrary producing lists
     */
    public static <T> Arbitrary<java.util.List<T>> graphqlList(Arbitrary<T> elementArbitrary) {
        return elementArbitrary.list().ofMinSize(0).ofMaxSize(10);
    }

    /**
     * Generates lists of GraphQL items with specified size constraints.
     *
     * @param <T> the element type
     * @param elementArbitrary arbitrary for list elements
     * @param minSize minimum list size (inclusive)
     * @param maxSize maximum list size (inclusive)
     * @return arbitrary producing lists
     */
    public static <T> Arbitrary<java.util.List<T>> graphqlList(
        Arbitrary<T> elementArbitrary,
        int minSize,
        int maxSize
    ) {
        return elementArbitrary.list().ofMinSize(minSize).ofMaxSize(maxSize);
    }

    /**
     * Generates non-empty lists of GraphQL items (1-10 elements).
     *
     * @param <T> the element type
     * @param elementArbitrary arbitrary for list elements
     * @return arbitrary producing non-empty lists
     */
    public static <T> Arbitrary<java.util.List<T>> nonEmptyGraphqlList(Arbitrary<T> elementArbitrary) {
        return elementArbitrary.list().ofMinSize(1).ofMaxSize(10);
    }
}
