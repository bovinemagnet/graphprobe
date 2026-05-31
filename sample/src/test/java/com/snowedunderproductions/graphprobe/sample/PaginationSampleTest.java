package com.snowedunderproductions.graphprobe.sample;

import com.snowedunderproductions.graphprobe.client.PaginationTestHelper;
import com.snowedunderproductions.graphprobe.client.SimpleGraphQLClient;
import com.snowedunderproductions.graphprobe.sample.generated.client.ProductsGraphQLQuery;
import com.snowedunderproductions.graphprobe.sample.generated.client.ProductsProjectionRoot;
import com.snowedunderproductions.graphprobe.sample.support.StubGraphQLServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Demonstrates {@link PaginationTestHelper} driving a typed, generated GraphQL
 * query through every page returned by the stub server.
 *
 * <p>The helper appends {@code [0]} and {@code [pageSize-1]} to the supplied
 * JsonPath to probe the first and last element of each page, so the path passed
 * is the full {@code "$.data.products"}.
 *
 * @author Paul Snow
 */
class PaginationSampleTest {

    private StubGraphQLServer stub;

    @BeforeEach
    void startStub() throws IOException {
        // 7 products at pageSize 3 -> pages of 3, 3, 1.
        stub = StubGraphQLServer.start().productTotal(7);
    }

    @AfterEach
    void stopStub() {
        stub.stop();
    }

    @Test
    void paginatesThroughAllProductPages() {
        SimpleGraphQLClient client =
            new SimpleGraphQLClient(stub.endpoint(), "Authorization", "");

        BiFunction<String, String, String> jsonCaller = (query, jsonPath) -> {
            try {
                return client.simpleJsonCall(query, jsonPath);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };

        PaginationTestHelper.paginateAndAssert(
            jsonCaller,
            pageNumber -> new ProductsGraphQLQuery.Builder()
                .pageNumber(pageNumber)
                .pageSize(3)
                .build(),
            new ProductsProjectionRoot<>().id().name(),
            "$.data.products",
            "paginatesThroughAllProductPages",
            3,
            10,
            "id", "name");
    }
}
