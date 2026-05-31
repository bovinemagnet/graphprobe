package com.snowedunderproductions.graphprobe.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.snowedunderproductions.graphprobe.jqwik.annotations.GraphQLProperty;
import com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLArbitraries;
import com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLFuzzingArbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Provide;

/**
 * Demonstrates property-based testing with {@link GraphQLProperty} and the
 * framework's reusable arbitraries.
 *
 * <p>These properties exercise the generators directly so they run with no GraphQL
 * endpoint. In a real suite the generated values would be fed into queries against
 * the service under test, asserting that valid inputs succeed and that malicious or
 * malformed inputs are rejected or sanitised.
 */
class PropertyBasedSampleTest {

    @GraphQLProperty(tries = 50)
    void generatedEmailsAreWellFormed(@ForAll("emails") String email) {
        assertThat(email).contains("@");
    }

    @Provide
    Arbitrary<String> emails() {
        return GraphQLArbitraries.email();
    }

    @GraphQLProperty(tries = 30)
    void sqlInjectionPayloadsAreGenerated(@ForAll("sqlPayloads") String payload) {
        // A real test would send this payload to a resolver and assert the API
        // does not leak data or error abnormally. Here we just confirm the
        // fuzzing generator yields usable payloads.
        assertThat(payload).isNotNull();
    }

    @Provide
    Arbitrary<String> sqlPayloads() {
        return GraphQLFuzzingArbitraries.sqlInjectionPayloads();
    }
}
