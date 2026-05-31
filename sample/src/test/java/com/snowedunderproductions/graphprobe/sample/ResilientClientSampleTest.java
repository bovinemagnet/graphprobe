package com.snowedunderproductions.graphprobe.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.snowedunderproductions.graphprobe.client.GraphQLRequestMetrics;
import com.snowedunderproductions.graphprobe.client.SimpleGraphQLClient;
import com.snowedunderproductions.graphprobe.sample.support.StubGraphQLServer;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Demonstrates {@link SimpleGraphQLClient}'s built-in resilience: a transient
 * HTTP 503 on a read-only query is retried once and then succeeds, and the retry
 * is reflected in {@link GraphQLRequestMetrics}.
 *
 * <p>The retry count is global, so the test compares the {@code retries=} value
 * before and after rather than asserting an absolute number.
 *
 * @author Paul Snow
 */
class ResilientClientSampleTest {

    private static final Pattern RETRIES = Pattern.compile("retries=(\\d+)");

    @Test
    void retriesTransientFailureAndSucceeds() throws IOException {
        StubGraphQLServer stub = StubGraphQLServer.start().failFirst(1);
        try {
            SimpleGraphQLClient client =
                new SimpleGraphQLClient(stub.endpoint(), "Authorization", "");

            long retriesBefore = currentRetries();

            // A read-only query is idempotent, so the client retries the 503 once.
            String ownerName = client.executeQuery(
                "{ product(id: \"1\") { owner { name } } }",
                "$.data.product.owner.name");

            long retriesAfter = currentRetries();

            assertThat(ownerName).isEqualTo("Ada Lovelace");
            assertThat(retriesAfter).isGreaterThan(retriesBefore);
        } finally {
            stub.stop();
        }
    }

    private static long currentRetries() {
        Matcher m = RETRIES.matcher(GraphQLRequestMetrics.formatSummary());
        assertThat(m.find()).as("metrics summary should expose a retries= count").isTrue();
        return Long.parseLong(m.group(1));
    }
}
