/**
 * JUnit {@link org.junit.jupiter.params.provider.ArgumentsProvider ArgumentsProvider} bridge
 * that adapts jqwik {@link net.jqwik.api.Arbitrary Arbitrary} instances into the
 * {@code @ParameterizedTest} /
 * {@link com.snowedunderproductions.graphprobe.annotations.DynamicSource @DynamicSource}
 * mechanism.
 *
 * <p>The single type in this package,
 * {@link com.snowedunderproductions.graphprobe.jqwik.providers.JqwikArgumentsProvider}, is an
 * abstract base class whose subclasses declare one or more jqwik arbitraries via
 * {@link com.snowedunderproductions.graphprobe.jqwik.providers.JqwikArgumentsProvider#getArbitraries()}.
 * At test-execution time the base class samples the arbitraries and streams the resulting
 * {@link org.junit.jupiter.params.provider.Arguments} tuples to the JUnit engine, exactly as a
 * database-backed
 * {@link com.snowedunderproductions.graphprobe.database.BaseArgumentsProvider} would.
 *
 * <h3>Key responsibilities</h3>
 * <ul>
 *   <li><strong>Arbitrary-to-Arguments translation</strong> — samples each
 *       {@link net.jqwik.api.Arbitrary} a configurable number of times
 *       (see {@link com.snowedunderproductions.graphprobe.jqwik.providers.JqwikArgumentsProvider#getSampleCount()})
 *       and assembles the values into
 *       {@link org.junit.jupiter.params.provider.Arguments} tuples whose position matches
 *       the test method's parameter list.</li>
 *   <li><strong>Reproducibility</strong> — exposes
 *       {@link com.snowedunderproductions.graphprobe.jqwik.providers.JqwikArgumentsProvider#getRandomSeed()}
 *       so that a fixed seed can be supplied for deterministic test runs.</li>
 *   <li><strong>Composability with the framework</strong> — because
 *       {@code JqwikArgumentsProvider} implements
 *       {@link org.junit.jupiter.params.provider.ArgumentsProvider}, it slots directly into
 *       {@link com.snowedunderproductions.graphprobe.annotations.DynamicSource#argumentsProvider()}
 *       without any additional glue code.</li>
 * </ul>
 *
 * <h3>Relationship to other jqwik packages</h3>
 * <p>Subclasses typically compose arbitraries from
 * {@link com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLArbitraries} (standard
 * GraphQL scalars and patterns) or
 * {@link com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLFuzzingArbitraries}
 * (injection payloads, edge-case strings, boundary values) and may also draw from
 * {@link com.snowedunderproductions.graphprobe.jqwik.arbitraries.DatabaseArbitraryProvider}
 * to sample real rows from a running database.
 *
 * <h3>Typical usage</h3>
 * <pre>{@code
 * // 1. Declare a provider subclass:
 * public class UserDataProvider extends JqwikArgumentsProvider {
 *     @Override
 *     protected Arbitrary<?>[] getArbitraries() {
 *         return new Arbitrary<?>[] {
 *             GraphQLArbitraries.graphqlId(),      // first parameter  — userId
 *             GraphQLArbitraries.graphqlString(),  // second parameter — userName
 *             GraphQLArbitraries.email()           // third parameter  — email
 *         };
 *     }
 *
 *     @Override
 *     protected int getSampleCount() { return 50; }
 * }
 *
 * // 2. Reference it from a parameterised test:
 * @ParameterizedTest
 * @DynamicSource(argumentsProvider = UserDataProvider.class)
 * void testUserCreation(String userId, String userName, String email) {
 *     // test logic
 * }
 * }</pre>
 *
 * @see com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLArbitraries
 * @see com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLFuzzingArbitraries
 * @see com.snowedunderproductions.graphprobe.annotations.DynamicSource
 * @see com.snowedunderproductions.graphprobe.database.BaseArgumentsProvider
 */
package com.snowedunderproductions.graphprobe.jqwik.providers;
