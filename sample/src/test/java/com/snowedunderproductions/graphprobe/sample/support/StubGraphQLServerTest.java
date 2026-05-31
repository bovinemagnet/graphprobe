package com.snowedunderproductions.graphprobe.sample.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snowedunderproductions.graphprobe.client.SimpleGraphQLClient;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Tests for the in-process {@link StubGraphQLServer} used by the sample suite.
 *
 * @author Paul Snow
 */
class StubGraphQLServerTest {

    @Test
    void servesOnlyTheRequestedProductPage() throws IOException {
        StubGraphQLServer stub = StubGraphQLServer.start().productTotal(7);
        try {
            SimpleGraphQLClient client =
                new SimpleGraphQLClient(stub.endpoint(), "Authorization", "");

            String body =
                client.executeFullQuery("{ products(pageNumber: 0, pageSize: 3) { id } }");

            assertThat(body)
                .contains("\"id\":\"0\"")
                .contains("\"id\":\"2\"")
                .doesNotContain("\"id\":\"3\"");
        } finally {
            stub.stop();
        }
    }

    @Test
    void failFirstReturnsTransientErrorThenSucceeds() throws IOException {
        StubGraphQLServer stub = StubGraphQLServer.start().failFirst(1);
        try {
            SimpleGraphQLClient client =
                new SimpleGraphQLClient(stub.endpoint(), "Authorization", "");

            // Mutations are NOT retried by the client, so the first 503 surfaces.
            assertThatThrownBy(() ->
                    client.executeFullQuery("mutation { addProduct(input: null) { id } }"))
                .isInstanceOf(IOException.class);

            // The stub has now exhausted its failure budget and serves normally.
            String body =
                client.executeFullQuery("mutation { addProduct(input: null) { id } }");
            assertThat(body).contains("must not be null");
        } finally {
            stub.stop();
        }
    }
}
