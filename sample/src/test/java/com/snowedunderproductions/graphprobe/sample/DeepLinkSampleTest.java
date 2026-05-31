package com.snowedunderproductions.graphprobe.sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.snowedunderproductions.graphprobe.client.DeepLinkTestHelper;
import com.snowedunderproductions.graphprobe.client.SimpleGraphQLClient;
import com.snowedunderproductions.graphprobe.sample.support.StubGraphQLServer;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Demonstrates {@link DeepLinkTestHelper} validating nested and resolved fields
 * in a GraphQL response. The full response body from
 * {@link SimpleGraphQLClient#executeFullQuery(String)} is parsed once, then
 * {@code /}-separated paths navigate into the nested {@code owner} object and the
 * {@code tags} array.
 *
 * @author Paul Snow
 */
class DeepLinkSampleTest {

    private static final String CTX = "DeepLinkSampleTest";

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
    void assertsNestedOwnerAndTags() throws IOException {
        SimpleGraphQLClient client =
            new SimpleGraphQLClient(stub.endpoint(), "Authorization", "");

        String body = client.executeFullQuery(
            "{ product(id: \"1\") { id name owner { name email } tags } }");

        JsonNode root = DeepLinkTestHelper.parseJson(body, CTX);

        DeepLinkTestHelper.assertFieldEquals(root, "data/product/owner/name", "Ada Lovelace", CTX);
        DeepLinkTestHelper.assertFieldExists(root, "data/product/owner/email", CTX);
        DeepLinkTestHelper.assertArrayNotEmpty(root, "data/product/tags", CTX);
    }
}
