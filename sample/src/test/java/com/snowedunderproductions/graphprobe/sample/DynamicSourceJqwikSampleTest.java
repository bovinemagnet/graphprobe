package com.snowedunderproductions.graphprobe.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.snowedunderproductions.graphprobe.annotations.DynamicSource;
import com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLArbitraries;
import com.snowedunderproductions.graphprobe.jqwik.providers.JqwikArgumentsProvider;
import net.jqwik.api.Arbitrary;
import org.junit.jupiter.params.ParameterizedTest;

/**
 * Demonstrates the {@code @ParameterizedTest} + {@link DynamicSource} mechanism fed
 * by jqwik arbitraries through {@link JqwikArgumentsProvider}.
 *
 * <p>This is the bridge that lets a project reuse familiar JUnit parameterised tests
 * while sourcing the data from the framework's generators — no database or CSV file
 * required, so it runs offline.
 */
class DynamicSourceJqwikSampleTest {

    /** Generates {@code (id, email)} pairs from the framework's arbitraries. */
    public static class UserDataProvider extends JqwikArgumentsProvider {

        @Override
        protected Arbitrary<?>[] getArbitraries() {
            return new Arbitrary<?>[] {
                GraphQLArbitraries.graphqlId(),
                GraphQLArbitraries.email()
            };
        }

        @Override
        protected int getSampleCount() {
            return 10;
        }
    }

    @ParameterizedTest
    @DynamicSource(argumentsProvider = UserDataProvider.class)
    void receivesGeneratedUserData(String id, String email) {
        assertThat(id).isNotBlank();
        assertThat(email).contains("@");
    }
}
