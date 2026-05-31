package com.snowedunderproductions.graphprobe.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.snowedunderproductions.graphprobe.client.SimpleGraphQLClient;
import com.snowedunderproductions.graphprobe.client.TestResponse;
import com.snowedunderproductions.graphprobe.sample.support.StubGraphQLServer;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Demonstrates negative-path testing with
 * {@link SimpleGraphQLClient#executeQueryOrPossibleSpecificError}: an invalid
 * mutation input is expected to surface a specific error message rather than data.
 *
 * @author Paul Snow
 */
class MutationErrorSampleTest {

    private StubGraphQLServer stub;

    @BeforeEach
    void startStub() throws IOException {
        stub = StubGraphQLServer.start();
    }

    @AfterEach
    void stopStub() {
        stub.stop();
    }

    @Test
    void rejectsNullMutationInputWithExpectedError() throws IOException {
        SimpleGraphQLClient client =
            new SimpleGraphQLClient(stub.endpoint(), "Authorization", "");

        TestResponse response = client.executeQueryOrPossibleSpecificError(
            "mutation { addProduct(input: null) { id errors } }",
            Optional.empty(),
            Optional.of("must not be null"));

        assertThat(response.success())
            .as("expected error message should be recognised as success")
            .isTrue();
        assertThat(response.error()).contains("must not be null");
    }
}
