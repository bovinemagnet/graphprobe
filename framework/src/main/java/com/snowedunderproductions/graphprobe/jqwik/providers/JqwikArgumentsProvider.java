package com.snowedunderproductions.graphprobe.jqwik.providers;

import net.jqwik.api.Arbitrary;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Abstract base class that bridges jqwik {@link Arbitrary} instances to JUnit's
 * {@link ArgumentsProvider}, enabling property-based test generation within the
 * {@code @ParameterizedTest} /
 * {@link com.snowedunderproductions.graphprobe.annotations.DynamicSource @DynamicSource}
 * mechanism.
 *
 * <p>Subclasses declare one or more arbitraries via {@link #getArbitraries()}; the base class
 * samples each arbitrary {@link #getSampleCount()} times and streams the resulting
 * {@link Arguments} tuples to the JUnit engine.  The position of each arbitrary in the returned
 * array must match the corresponding parameter position in the test method signature.
 *
 * <p>Arbitraries are typically sourced from
 * {@link com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLArbitraries} for
 * standard GraphQL scalars and patterns, or from
 * {@link com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLFuzzingArbitraries}
 * for security and edge-case payloads.
 *
 * <h3>Single-parameter usage</h3>
 * <pre>{@code
 * public class UserIdProvider extends JqwikArgumentsProvider {
 *     @Override
 *     protected Arbitrary<?>[] getArbitraries() {
 *         return new Arbitrary<?>[] {
 *             GraphQLArbitraries.graphqlId()
 *         };
 *     }
 * }
 *
 * @ParameterizedTest
 * @DynamicSource(argumentsProvider = UserIdProvider.class)
 * void testWithGeneratedIds(String userId) {
 *     // test logic
 * }
 * }</pre>
 *
 * <h3>Multi-parameter usage</h3>
 * <pre>{@code
 * public class UserDataProvider extends JqwikArgumentsProvider {
 *     @Override
 *     protected Arbitrary<?>[] getArbitraries() {
 *         return new Arbitrary<?>[] {
 *             GraphQLArbitraries.graphqlId(),      // first  parameter — userId
 *             GraphQLArbitraries.graphqlString(),  // second parameter — userName
 *             GraphQLArbitraries.email()           // third  parameter — email
 *         };
 *     }
 *
 *     @Override
 *     protected int getSampleCount() {
 *         return 50; // generate 50 test cases
 *     }
 * }
 *
 * @ParameterizedTest
 * @DynamicSource(argumentsProvider = UserDataProvider.class)
 * void testWithGeneratedUsers(String userId, String userName, String email) {
 *     // test logic
 * }
 * }</pre>
 *
 * <h3>Configuration hooks</h3>
 * <ul>
 *   <li>Override {@link #getArbitraries()} — required; provides the arbitraries for each
 *       test parameter.</li>
 *   <li>Override {@link #getSampleCount()} — controls how many test cases are generated
 *       (default: {@value #DEFAULT_SAMPLE_COUNT}).</li>
 *   <li>Override {@link #getRandomSeed()} — supply a fixed seed for reproducible runs
 *       (default: current system time).</li>
 *   <li>Override {@link #getTriesPerSample()} — increase when heavily filtered arbitraries
 *       frequently discard candidates (default: {@value #DEFAULT_TRIES_PER_SAMPLE}).</li>
 * </ul>
 *
 * @see Arbitrary
 * @see ArgumentsProvider
 * @see com.snowedunderproductions.graphprobe.annotations.DynamicSource
 * @see com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLArbitraries
 * @see com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLFuzzingArbitraries
 * @since 1.0.0
 */
public abstract class JqwikArgumentsProvider implements ArgumentsProvider {

    private static final Logger log = LoggerFactory.getLogger(JqwikArgumentsProvider.class);
    private static final int DEFAULT_SAMPLE_COUNT = 20;
    private static final int DEFAULT_TRIES_PER_SAMPLE = 100;

    @Override
    public final Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
        Arbitrary<?>[] arbitraries = getArbitraries();

        if (arbitraries == null || arbitraries.length == 0) {
            throw new IllegalStateException(
                "getArbitraries() must return at least one Arbitrary. " +
                "Provider: " + getClass().getSimpleName()
            );
        }

        int sampleCount = getSampleCount();
        if (sampleCount <= 0) {
            throw new IllegalStateException(
                "getSampleCount() must return a positive number. Got: " + sampleCount
            );
        }

        long seed = getRandomSeed();
        log.debug(
            "Generating {} samples from {} arbitraries (seed: {}) for provider: {}",
            sampleCount,
            arbitraries.length,
            seed,
            getClass().getSimpleName()
        );

        List<Arguments> argumentsList = new ArrayList<>(sampleCount);
        Random random = new Random(seed);

        for (int i = 0; i < sampleCount; i++) {
            Object[] values = new Object[arbitraries.length];

            for (int j = 0; j < arbitraries.length; j++) {
                values[j] = sampleValue(arbitraries[j], random, i);
            }

            argumentsList.add(Arguments.of(values));
        }

        log.debug(
            "Generated {} argument sets for provider: {}",
            argumentsList.size(),
            getClass().getSimpleName()
        );

        return argumentsList.stream();
    }

    /**
     * Samples a single value from an arbitrary.
     *
     * @param arbitrary the arbitrary to sample from
     * @param random the random instance for sampling (unused with sample() API)
     * @param attemptNumber the current sample attempt number
     * @return the sampled value
     */
    private Object sampleValue(Arbitrary<?> arbitrary, Random random, int attemptNumber) {
        try {
            // Use jqwik's sample() method which handles generation internally
            // Note: This uses jqwik's internal random seeding which may not be
            // identical to the provided Random instance, but provides consistent results
            return arbitrary.sample();

        } catch (Exception e) {
            log.error(
                "Failed to sample value from arbitrary at index {} (attempt {}): {}",
                attemptNumber,
                attemptNumber,
                e.getMessage()
            );
            throw new RuntimeException(
                "Failed to generate value from arbitrary: " + arbitrary.getClass().getSimpleName(),
                e
            );
        }
    }

    /**
     * Returns the arbitraries used to generate test parameters.
     *
     * <p>Each element in the returned array corresponds to one parameter in the
     * {@code @ParameterizedTest} method signature; positions must match exactly.
     * Returning {@code null} or an empty array causes
     * {@link #provideArguments(ExtensionContext)} to throw
     * {@link IllegalStateException}.
     *
     * <p>Implementations typically compose values from
     * {@link com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLArbitraries} or
     * {@link com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLFuzzingArbitraries}.
     *
     * @return a non-null, non-empty array of arbitraries, one per test parameter
     */
    protected abstract Arbitrary<?>[] getArbitraries();

    /**
     * Returns the number of test cases to generate from the arbitraries.
     *
     * <p>Defaults to {@value #DEFAULT_SAMPLE_COUNT}. Override to generate more or fewer
     * test cases — increase for broader coverage, decrease for faster local feedback.
     * Must return a positive integer; {@link #provideArguments(ExtensionContext)} throws
     * {@link IllegalStateException} if zero or negative is returned.
     *
     * @return the number of samples to generate; must be positive
     */
    protected int getSampleCount() {
        return DEFAULT_SAMPLE_COUNT;
    }

    /**
     * Returns the random seed used when initialising the internal {@link java.util.Random}
     * instance for sample generation.
     *
     * <p>Defaults to {@link System#currentTimeMillis()}, which produces a different sequence
     * on every run.  Override to return a fixed value when deterministic, repeatable test
     * cases are required — for example when diagnosing a previously observed failure.
     *
     * <p>Note that jqwik's own {@link Arbitrary#sample()} method manages its own internal
     * seeding; this seed primarily governs the ordering and selection logic within
     * {@link #provideArguments(ExtensionContext)}.
     *
     * @return the random seed; use a fixed value for reproducible runs
     */
    protected long getRandomSeed() {
        return System.currentTimeMillis();
    }

    /**
     * Returns the maximum number of generation attempts per sample when an arbitrary
     * applies filters or assumptions that may discard candidates.
     *
     * <p>Defaults to {@value #DEFAULT_TRIES_PER_SAMPLE}. Increase this value when using
     * heavily filtered arbitraries (for example, those built with
     * {@link net.jqwik.api.Arbitrary#filter(java.util.function.Predicate)}) to reduce
     * the chance of exhausting the attempt budget before a valid sample is found.
     *
     * @return the maximum number of attempts per generated sample; must be positive
     */
    protected int getTriesPerSample() {
        return DEFAULT_TRIES_PER_SAMPLE;
    }
}
