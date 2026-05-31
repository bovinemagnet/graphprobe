package com.snowedunderproductions.graphprobe.jqwik.annotations;

import net.jqwik.api.Property;

import java.lang.annotation.*;

/**
 * Marks a method as a jqwik property test specifically for GraphQL API testing.
 * <p>
 * This is a meta-annotation that composes jqwik's {@link Property @Property}
 * with GraphQL-specific semantics.  It makes the intent clearer and exposes the
 * most commonly tuned attributes so that consuming test classes do not need to
 * import jqwik types directly for routine configuration.
 * </p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @GraphQLProperty
 * void userQueriesReturnValidData(@ForAll("userIds") String userId) {
 *     GraphQLTestClient client = new GraphQLTestClient();
 *     String result = client.executeQuery(
 *         String.format("{ user(id: \"%s\") { name } }", userId),
 *         "$.data.user.name"
 *     );
 *     assertThat(result).isNotEmpty();
 * }
 *
 * @Provide
 * Arbitrary<String> userIds() {
 *     return GraphQLArbitraries.graphqlId();  // see GraphQLArbitraries for full catalogue
 * }
 * }</pre>
 *
 * <h3>Configuration attributes</h3>
 * <ul>
 *   <li>{@link #tries()} — number of test iterations (default: {@code 100})</li>
 *   <li>{@link #seed()} — random seed for reproducibility (default: random)</li>
 *   <li>{@link #shrinking()} — shrinking mode for failing tests (default: {@code BOUNDED})</li>
 *   <li>{@link #maxDiscardRatio()} — maximum ratio of discarded tests (default: {@code 5})</li>
 *   <li>{@link #generation()} — arbitrary generation mode (default: {@code AUTO})</li>
 *   <li>{@link #edgeCases()} — edge-case injection mode (default: {@code MIXIN})</li>
 * </ul>
 *
 * @see Property
 * @see GraphQLArbitrary
 * @see com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLArbitraries
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Property
public @interface GraphQLProperty {

    /**
     * Number of tries to execute the property test.
     * <p>
     * Default is 100. Increase for more thorough testing, decrease for faster feedback.
     * </p>
     *
     * @return the number of tries
     */
    int tries() default 100;

    /**
     * Random seed for reproducible property testing.
     * <p>
     * Use a fixed seed to make failing tests reproducible. Default is random.
     * </p>
     *
     * @return the random seed, or empty string for random seed
     */
    String seed() default "";

    /**
     * Shrinking mode for minimising failing test cases.
     * <ul>
     *   <li>{@code BOUNDED} (default) — shrink within a reasonable time limit.</li>
     *   <li>{@code FULL} — shrink exhaustively; may be slow on large arbitraries.</li>
     *   <li>{@code OFF} — no shrinking; faster but produces less helpful failure messages.</li>
     * </ul>
     *
     * @return the shrinking mode
     */
    net.jqwik.api.ShrinkingMode shrinking() default net.jqwik.api.ShrinkingMode.BOUNDED;

    /**
     * Maximum ratio of discarded to successful test executions.
     * <p>
     * Default is 5, meaning up to 5 discarded tests per successful test.
     * Increase if using heavily filtered arbitraries.
     * </p>
     *
     * @return the maximum discard ratio
     */
    int maxDiscardRatio() default 5;

    /**
     * Generation mode for arbitraries.
     * <ul>
     *   <li>{@code AUTO} (default) — jqwik selects the most appropriate strategy.</li>
     *   <li>{@code RANDOMIZED} — fully randomised generation on every run.</li>
     *   <li>{@code EXHAUSTIVE} — attempts to enumerate all possible values; suitable only for
     *       small, finite domains.</li>
     *   <li>{@code DATA_DRIVEN} — uses external data sources (e.g.
     *       {@link com.snowedunderproductions.graphprobe.jqwik.arbitraries.DatabaseArbitraryProvider}).</li>
     * </ul>
     *
     * @return the generation mode
     */
    net.jqwik.api.GenerationMode generation() default net.jqwik.api.GenerationMode.AUTO;

    /**
     * Edge-cases mode for testing boundary values.
     * <ul>
     *   <li>{@code MIXIN} (default) — interleave edge cases with randomly generated values.</li>
     *   <li>{@code FIRST} — exhaust all edge cases before producing random values.</li>
     *   <li>{@code NONE} — skip edge-case generation entirely.</li>
     * </ul>
     *
     * @return the edge cases mode
     */
    net.jqwik.api.EdgeCasesMode edgeCases() default net.jqwik.api.EdgeCasesMode.MIXIN;
}
