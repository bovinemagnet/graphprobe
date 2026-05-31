package com.snowedunderproductions.graphprobe.jqwik.examples;

import com.snowedunderproductions.graphprobe.jqwik.annotations.GraphQLArbitrary;
import com.snowedunderproductions.graphprobe.jqwik.annotations.GraphQLProperty;
import com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLArbitraries;
import com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLFuzzingArbitraries;
import com.snowedunderproductions.graphprobe.jqwik.providers.JqwikArgumentsProvider;
import net.jqwik.api.*;
import org.junit.jupiter.params.ParameterizedTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive examples demonstrating all 4 jqwik integration use cases:
 * <p>
 * 1. Generate random GraphQL inputs for fuzzing
 * 2. Provide as an alternative to database/CSV providers
 * 3. Use jqwik as an available dependency
 * 4. Build reusable jqwik arbitraries/generators
 * </p>
 *
 * <p>
 * NOTE: These are example tests to demonstrate the API.
 * They use mock assertions since there's no actual GraphQL server in the framework.
 * In real usage, these would call GraphQLTestClient to test actual APIs.
 * </p>
 */
class GraphQLJqwikExampleTest {

    // ==================== Use Case 1: Fuzzing with Random Inputs ====================

    /**
     * Example 1a: Fuzzing with injection payloads to test security.
     * Tests that the API properly sanitizes malicious inputs.
     */
    @GraphQLProperty(tries = 50)
    @GraphQLArbitrary(
        provider = "sqlInjectionInputs",
        description = "SQL injection attack payloads",
        source = GraphQLArbitrary.DataSource.GENERATED
    )
    void fuzzingWithInjectionPayloads(@ForAll("sqlInjectionInputs") String maliciousInput) {
        // In real usage:
        // GraphQLTestClient client = new GraphQLTestClient();
        // TestResponse response = client.executeQueryOrPossibleSpecificError(
        //     String.format("{ user(id: \"%s\") { name } }", maliciousInput),
        //     Optional.empty(),
        //     Optional.of("Invalid input")
        // );
        // assertThat(response.hasError() || !response.containsData("DROP")).isTrue();

        // Example assertion showing the framework works:
        assertThat(maliciousInput).isNotNull();
        System.out.println("Testing with injection payload: " + maliciousInput);
    }

    @Provide
    Arbitrary<String> sqlInjectionInputs() {
        return GraphQLFuzzingArbitraries.sqlInjectionPayloads();
    }

    /**
     * Example 1b: Fuzzing with extremely long strings to test size limits.
     */
    @GraphQLProperty(tries = 20)
    void fuzzingWithLongStrings(@ForAll("extremeStrings") String longInput) {
        // Test that API properly handles or rejects oversized inputs
        assertThat(longInput.length()).isGreaterThanOrEqualTo(1000);
        System.out.println("Testing with string length: " + longInput.length());
    }

    @Provide
    Arbitrary<String> extremeStrings() {
        return GraphQLFuzzingArbitraries.extremelyLongStrings(1000, 10000);
    }

    /**
     * Example 1c: Fuzzing with malformed GraphQL syntax.
     */
    @GraphQLProperty(tries = 30)
    void fuzzingWithMalformedQueries(@ForAll("malformedQueries") String query) {
        // Test that parser properly rejects malformed queries
        assertThat(query).isNotNull();
        System.out.println("Testing malformed query: " + query);
    }

    @Provide
    Arbitrary<String> malformedQueries() {
        return GraphQLFuzzingArbitraries.malformedGraphQL();
    }

    // ==================== Use Case 2: Alternative to Database/CSV Providers ====================

    /**
     * Example 2a: Using jqwik with JUnit's @ParameterizedTest via JqwikArgumentsProvider.
     * This shows jqwik as an alternative to database/CSV providers.
     */
    @ParameterizedTest
    @com.snowedunderproductions.graphprobe.annotations.DynamicSource(
        argumentsProvider = GeneratedUserIdProvider.class
    )
    void jqwikAsAlternativeProvider(String userId, String userName) {
        // Use jqwik-generated data with @DynamicSource pattern
        assertThat(userId).isNotEmpty();
        assertThat(userName).isNotEmpty();
        System.out.println("Testing with generated user: " + userId + " - " + userName);
    }

    /**
     * Example provider bridging jqwik to @DynamicSource.
     */
    public static class GeneratedUserIdProvider extends JqwikArgumentsProvider {
        @Override
        protected Arbitrary<?>[] getArbitraries() {
            return new Arbitrary<?>[]{
                GraphQLArbitraries.graphqlId(),           // userId
                GraphQLArbitraries.graphqlString(5, 20)   // userName
            };
        }

        @Override
        protected int getSampleCount() {
            return 10; // Generate 10 test cases
        }
    }

    /**
     * Example 2b: Database-driven jqwik arbitrary (requires database connection).
     * Commented out as it requires actual database setup.
     */
    // @GraphQLProperty
    // void jqwikWithDatabaseData(@ForAll("databaseUserIds") String userId) {
    //     // Use real database data with jqwik property testing
    //     assertThat(userId).isNotEmpty();
    // }
    //
    // @Provide
    // Arbitrary<String> databaseUserIds() {
    //     return DatabaseArbitraryProvider
    //         .fromQuery("SELECT user_id FROM users LIMIT 100")
    //         .extractString("user_id");
    // }

    // ==================== Use Case 3: jqwik as Available Dependency ====================

    /**
     * Example 3a: Using jqwik directly for property-based testing.
     * This demonstrates that jqwik is available as a dependency.
     */
    @Property
    void jqwikAvailableForDirectUse(@ForAll int value) {
        // Standard jqwik property test
        assertThat(value * 2).isEven();
    }

    /**
     * Example 3b: Using jqwik's built-in arbitraries.
     */
    @Property
    void usingJqwikBuiltInArbitraries(
        @ForAll String text,
        @ForAll int number
    ) {
        // jqwik is fully available for standard property testing
        assertThat(text).isNotNull();
        // Just verify jqwik works - don't make assumptions about values
    }

    // ==================== Use Case 4: Reusable GraphQL Arbitraries ====================

    /**
     * Example 4a: Using GraphQLArbitraries for common GraphQL types.
     */
    @GraphQLProperty
    @GraphQLArbitrary(
        provider = "graphqlIds",
        description = "GraphQL ID scalar values",
        domain = "UUIDs, numeric IDs, and alphanumeric strings"
    )
    void testWithGraphQLIds(@ForAll("graphqlIds") String id) {
        // Test with realistic GraphQL ID values
        assertThat(id).isNotEmpty();
        System.out.println("Testing with ID: " + id);
    }

    @Provide
    Arbitrary<String> graphqlIds() {
        return GraphQLArbitraries.graphqlId();
    }

    /**
     * Example 4b: Composing multiple GraphQL arbitraries.
     */
    @GraphQLProperty(tries = 50)
    void testWithMultipleGraphQLTypes(
        @ForAll("graphqlIds") String id,
        @ForAll("graphqlStrings") String name,
        @ForAll("emails") String email,
        @ForAll("graphqlInts") Integer age
    ) {
        // Test with multiple composed GraphQL types
        assertThat(id).isNotEmpty();
        assertThat(name).isNotEmpty();
        assertThat(email).contains("@");
        assertThat(age).isNotNegative();

        System.out.printf("User: id=%s, name=%s, email=%s, age=%d%n",
            id, name, email, age);
    }

    @Provide
    Arbitrary<String> graphqlStrings() {
        return GraphQLArbitraries.graphqlString(3, 50);
    }

    @Provide
    Arbitrary<String> emails() {
        return GraphQLArbitraries.email();
    }

    @Provide
    Arbitrary<Integer> graphqlInts() {
        return GraphQLArbitraries.graphqlInt(18, 120);
    }

    /**
     * Example 4c: Using pagination arbitraries.
     */
    @GraphQLProperty
    void testPaginationParameters(
        @ForAll("paginationLimits") Integer limit,
        @ForAll("paginationOffsets") Integer offset
    ) {
        // Test pagination with realistic values
        assertThat(limit).isBetween(1, 100);
        assertThat(offset).isNotNegative();

        System.out.printf("Pagination: limit=%d, offset=%d%n", limit, offset);
    }

    @Provide
    Arbitrary<Integer> paginationLimits() {
        return GraphQLArbitraries.paginationLimit();
    }

    @Provide
    Arbitrary<Integer> paginationOffsets() {
        return GraphQLArbitraries.paginationOffset();
    }

    /**
     * Example 4d: Using nullable arbitraries.
     */
    @GraphQLProperty
    void testNullableFields(@ForAll("nullableNames") String name) {
        // Test with nullable GraphQL fields
        // Name can be null or non-empty string
        if (name != null && !name.isEmpty()) {
            assertThat(name.length()).isGreaterThan(0);
        }
        System.out.println("Testing nullable name: " + name);
    }

    @Provide
    Arbitrary<String> nullableNames() {
        return GraphQLArbitraries.nullable(
            GraphQLArbitraries.graphqlString(),
            0.2 // 20% null probability
        );
    }

    /**
     * Example 4e: Using list arbitraries.
     */
    @GraphQLProperty
    void testGraphQLLists(@ForAll("userLists") java.util.List<String> userIds) {
        // Test with GraphQL list types
        assertThat(userIds).hasSizeBetween(0, 10);
        System.out.println("Testing with " + userIds.size() + " user IDs");
    }

    @Provide
    Arbitrary<java.util.List<String>> userLists() {
        return GraphQLArbitraries.graphqlList(
            GraphQLArbitraries.graphqlId(),
            0,
            10
        );
    }

    /**
     * Example 4f: Building custom domain arbitraries using combinators.
     */
    @GraphQLProperty
    void testCustomDomainObject(@ForAll("users") User user) {
        // Test with custom domain objects built from GraphQL arbitraries
        assertThat(user.id()).isNotEmpty();
        assertThat(user.name()).isNotEmpty();
        assertThat(user.email()).contains("@");

        System.out.println("Testing user: " + user);
    }

    @Provide
    Arbitrary<User> users() {
        return Combinators.combine(
            GraphQLArbitraries.graphqlId(),
            GraphQLArbitraries.graphqlString(3, 30),
            GraphQLArbitraries.email(),
            GraphQLArbitraries.graphqlInt(18, 100)
        ).as(User::new);
    }

    /**
     * Example domain object for testing.
     */
    record User(String id, String name, String email, Integer age) {
    }

    // ==================== Advanced Examples ====================

    /**
     * Example 5: Combining database and generated data (commented - requires DB).
     */
    // @GraphQLProperty
    // void combiningDatabaseAndGenerated(
    //     @ForAll("databaseProductIds") String productId,
    //     @ForAll("generatedQuantities") Integer quantity
    // ) {
    //     // Mix real database IDs with generated quantities
    //     assertThat(productId).isNotEmpty();
    //     assertThat(quantity).isPositive();
    // }
    //
    // @Provide
    // Arbitrary<String> databaseProductIds() {
    //     return DatabaseArbitraryProvider
    //         .fromQuery("SELECT product_id FROM products WHERE active = true LIMIT 50")
    //         .extractString("product_id");
    // }
    //
    // @Provide
    // Arbitrary<Integer> generatedQuantities() {
    //     return GraphQLArbitraries.graphqlInt(1, 1000);
    // }

    /**
     * Example 6: Edge case testing with boundary values.
     */
    @GraphQLProperty
    @GraphQLArbitrary(
        provider = "boundaryValues",
        description = "Numeric boundary values for edge case testing",
        includesEdgeCases = true
    )
    void testNumericBoundaries(@ForAll("boundaryValues") String numericValue) {
        // Test with numeric edge cases
        assertThat(numericValue).isNotEmpty();
        System.out.println("Testing boundary value: " + numericValue);
    }

    @Provide
    Arbitrary<String> boundaryValues() {
        return GraphQLFuzzingArbitraries.numericBoundaryValues();
    }

    /**
     * Example 7: Timestamp testing.
     */
    @GraphQLProperty
    void testTimestamps(@ForAll("timestamps") String timestamp) {
        // Test with ISO-8601 timestamps
        assertThat(timestamp).contains("T");
        System.out.println("Testing timestamp: " + timestamp);
    }

    @Provide
    Arbitrary<String> timestamps() {
        return GraphQLArbitraries.isoTimestamp();
    }
}
