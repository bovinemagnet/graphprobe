package com.snowedunderproductions.graphprobe.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.snowedunderproductions.graphprobe.config.EnvConfig;
import com.snowedunderproductions.graphprobe.config.TestProfile;
import com.netflix.graphql.dgs.client.codegen.BaseSubProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reusable pagination helper for integration tests that need to iterate through
 * multiple pages of a GraphQL query, asserting expected fields on each page.
 * <p>
 * Each page is fetched through a JSON caller using indexed access (e.g.
 * {@code allocated[0]}) to extract individual results, avoiding type-mismatch
 * issues with list deserialisation. The caller is a
 * {@link BiFunction BiFunction&lt;query, jsonPath, result&gt;} so the helper is not
 * bound to a single executor. A convenience overload delegates to
 * {@link SimpleGraphQLClient#simpleJsonCall(String, String)}.
 * <p>
 * Two flavours are provided:
 * <ul>
 *   <li>{@code paginateAndAssert} &mdash; <strong>fails</strong> the test if no results are returned</li>
 *   <li>{@code paginateAndAssume} &mdash; <strong>skips</strong> the test if no results are returned
 *       (useful when data may not be available in every environment)</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <p>Default page geometry is environment-driven (clamped by {@link TestProfile}):
 * <ul>
 *   <li>{@code GRAPHQL_FIRST_PAGE} — first page index (default {@value #DEFAULT_FIRST_PAGE})</li>
 *   <li>{@code GRAPHQL_PAGE_SIZE} — results per page (default {@value #DEFAULT_PAGE_SIZE})</li>
 *   <li>{@code GRAPHQL_MAX_PAGE} — maximum page index, inclusive (default {@value #DEFAULT_MAX_PAGE})</li>
 * </ul>
 *
 * @author Paul Snow
 * @since 0.0.0
 */
public final class PaginationTestHelper {

	private static final Logger log = LoggerFactory.getLogger(PaginationTestHelper.class);

	/** Default first-page index when {@code GRAPHQL_FIRST_PAGE} is unset. */
	public static final int DEFAULT_FIRST_PAGE = 0;

	/** Default page size when {@code GRAPHQL_PAGE_SIZE} is unset. */
	public static final int DEFAULT_PAGE_SIZE = 100;

	/** Default maximum page (inclusive) when {@code GRAPHQL_MAX_PAGE} is unset. */
	public static final int DEFAULT_MAX_PAGE = 10;

	private PaginationTestHelper() {
		// utility class
	}

	/**
	 * Returns the configured first-page index, sourced from {@code GRAPHQL_FIRST_PAGE}
	 * (default {@value #DEFAULT_FIRST_PAGE}).
	 *
	 * @return the zero-based first page index to begin pagination from
	 */
	public static int firstPage() {
		return EnvConfig.getInt("GRAPHQL_FIRST_PAGE", DEFAULT_FIRST_PAGE);
	}

	/**
	 * Returns the configured page size, sourced from {@code GRAPHQL_PAGE_SIZE}
	 * (default {@value #DEFAULT_PAGE_SIZE}).
	 *
	 * @return the number of results to request per page
	 */
	public static int pageSize() {
		return EnvConfig.getInt("GRAPHQL_PAGE_SIZE", DEFAULT_PAGE_SIZE);
	}

	/**
	 * Returns the configured maximum page index (inclusive), sourced from
	 * {@code GRAPHQL_MAX_PAGE} (default {@value #DEFAULT_MAX_PAGE}) and clamped by
	 * the active {@link com.snowedunderproductions.graphprobe.config.TestProfile}.
	 *
	 * @return the maximum page number to iterate up to
	 */
	public static int maxPage() {
		return TestProfile.clampMaxPage(EnvConfig.getInt("GRAPHQL_MAX_PAGE", DEFAULT_MAX_PAGE));
	}

	// ── paginateAndAssert ───────────────────────────────────────────────

	/**
	 * Paginates through a GraphQL query, asserting that each page contains the expected
	 * fields. <strong>Fails</strong> the test if no results are returned on the first page.
	 *
	 * @param jsonCaller     the {@code (query, jsonPath) -> result} caller used to fetch each element
	 * @param queryBuilder   a function that accepts a page number and returns the built {@link GraphQLQuery}
	 * @param projection     the field projection to apply to the query
	 * @param jsonPath       the JSONPath expression for the result list (e.g. "allocated")
	 * @param testName       the test name for logging
	 * @param pageSize       the number of results per page
	 * @param maxPage        the maximum page number to fetch (inclusive)
	 * @param expectedFields field names that must appear in each page's response
	 */
	@Step("Paginate and assert: {4}")
	public static void paginateAndAssert(BiFunction<String, String, String> jsonCaller, Function<Integer, GraphQLQuery> queryBuilder, BaseSubProjectionNode<?, ?> projection, String jsonPath, String testName, int pageSize, int maxPage, String... expectedFields) {
		doPaginate(jsonCaller, queryBuilder, projection, jsonPath, testName, pageSize, maxPage, false, expectedFields);
	}

	/**
	 * Convenience overload using the default page geometry and a {@link SimpleGraphQLClient}
	 * as the JSON caller.
	 *
	 * @see #paginateAndAssert(BiFunction, Function, BaseSubProjectionNode, String, String, int, int, String...)
	 */
	@Step("Paginate and assert: {2}")
	public static void paginateAndAssert(Function<Integer, GraphQLQuery> queryBuilder, BaseSubProjectionNode<?, ?> projection, String jsonPath, String testName, String... expectedFields) {
		paginateAndAssert(defaultJsonCaller(), queryBuilder, projection, jsonPath, testName, pageSize(), maxPage(), expectedFields);
	}

	// ── paginateAndAssume ───────────────────────────────────────────────

	/**
	 * Paginates through a GraphQL query, asserting that each page contains the expected
	 * fields. <strong>Skips</strong> the test if no results are returned on the first page
	 * (useful when test data may not be available in every environment).
	 *
	 * @param jsonCaller     the {@code (query, jsonPath) -> result} caller used to fetch each element
	 * @param queryBuilder   a function that accepts a page number and returns the built {@link GraphQLQuery}
	 * @param projection     the field projection to apply to the query
	 * @param jsonPath       the JSONPath expression for the result list (e.g. "allocated")
	 * @param testName       the test name for logging
	 * @param pageSize       the number of results per page
	 * @param maxPage        the maximum page number to fetch (inclusive)
	 * @param expectedFields field names that must appear in each page's response
	 */
	@Step("Paginate and assume: {4}")
	public static void paginateAndAssume(BiFunction<String, String, String> jsonCaller, Function<Integer, GraphQLQuery> queryBuilder, BaseSubProjectionNode<?, ?> projection, String jsonPath, String testName, int pageSize, int maxPage, String... expectedFields) {
		doPaginate(jsonCaller, queryBuilder, projection, jsonPath, testName, pageSize, maxPage, true, expectedFields);
	}

	/**
	 * Convenience overload using the default page geometry and a {@link SimpleGraphQLClient}
	 * as the JSON caller.
	 *
	 * @see #paginateAndAssume(BiFunction, Function, BaseSubProjectionNode, String, String, int, int, String...)
	 */
	@Step("Paginate and assume: {2}")
	public static void paginateAndAssume(Function<Integer, GraphQLQuery> queryBuilder, BaseSubProjectionNode<?, ?> projection, String jsonPath, String testName, String... expectedFields) {
		paginateAndAssume(defaultJsonCaller(), queryBuilder, projection, jsonPath, testName, pageSize(), maxPage(), expectedFields);
	}

	// ── default caller ──────────────────────────────────────────────────

	/**
	 * @return a JSON caller backed by a fresh {@link SimpleGraphQLClient}. Checked
	 *         {@link IOException}s are rethrown as {@link UncheckedIOException} so the
	 *         caller fits the {@link BiFunction} contract; the pagination loop converts
	 *         these into test failures with the offending query attached.
	 */
	private static BiFunction<String, String, String> defaultJsonCaller() {
		SimpleGraphQLClient client = new SimpleGraphQLClient();
		return (query, jsonPath) -> {
			try {
				return client.simpleJsonCall(query, jsonPath);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		};
	}

	// ── shared pagination loop ──────────────────────────────────────────

	/**
	 * Executes the pagination loop using the supplied JSON caller with indexed access
	 * (e.g. {@code allocated[0]}). Checks the first element of each page before
	 * proceeding &mdash; if no results are found, either skips the test
	 * ({@code assumeNoResults = true}) or fails it ({@code assumeNoResults = false}).
	 * <p>
	 * Each page is validated for expected fields. A page is considered full when
	 * the last index ({@code pageSize - 1}) returns a result. Stops when a page is
	 * not full, or after {@code maxPage} pages.
	 *
	 * @param assumeNoResults if {@code true}, skip the test when no results are found;
	 *                        if {@code false}, fail the test
	 */
	@Step("Pagination loop: {4}")
	private static void doPaginate(BiFunction<String, String, String> jsonCaller, Function<Integer, GraphQLQuery> queryBuilder, BaseSubProjectionNode<?, ?> projection, String jsonPath, String testName, int pageSize, int maxPage, boolean assumeNoResults, String... expectedFields) {
		Allure.parameter("jsonPath", jsonPath);
		Allure.parameter("pageSize", String.valueOf(pageSize));
		Allure.parameter("maxPage", String.valueOf(maxPage));
		int totalPages = 0;

		for (int pageNumber = firstPage(); pageNumber <= maxPage; pageNumber++) {
			GraphQLQueryRequest request = new GraphQLQueryRequest(queryBuilder.apply(pageNumber), projection);

			String query = request.serialize();
			log.debug("[{}] page {} query [{}]", testName, pageNumber, query);

			try {
				// Check first element of this page
				long startMs = System.currentTimeMillis();
				String firstResult = jsonCaller.apply(query, jsonPath + "[0]");
				long firstElapsed = System.currentTimeMillis() - startMs;

				if (firstResult == null || firstResult.isEmpty()) {
					log.info("[{}] page {} returned no results ({}ms), stopping pagination", testName, pageNumber, firstElapsed);

					if (totalPages == 0) {
						if (assumeNoResults) {
							assumeTrue(false, testName + ": no results returned — skipping (data may not be available)");
						} else {
							fail(testName + ": no results returned on first page\n" + query);
						}
					}
					break;
				}

				totalPages++;

				for (String field : expectedFields) {
					assertThat(firstResult).as("Page %d should contain field '%s'", pageNumber, field).contains(field);
				}

				// Check if page is full by probing the last index
				startMs = System.currentTimeMillis();
				String lastResult = jsonCaller.apply(query, jsonPath + "[" + (pageSize - 1) + "]");
				long lastElapsed = System.currentTimeMillis() - startMs;

				log.info("[{}] page {} — first: {}ms, last: {}ms, total: {}ms (pages so far: {})", testName, pageNumber, firstElapsed, lastElapsed, firstElapsed + lastElapsed, totalPages);
				Allure.step(String.format("[%s] page %d fetched — %dms (pages so far: %d)", testName, pageNumber, firstElapsed + lastElapsed, totalPages));

				if (lastResult == null || lastResult.isEmpty()) {
					log.info("[{}] partial page (fewer than {} results) on page {}, stopping pagination", testName, pageSize, pageNumber);
					break;
				}
			} catch (org.opentest4j.TestAbortedException e) {
				// paginateAndAssume's no-results skip (assumeTrue above) throws
				// TestAbortedException — let it propagate so the test SKIPS, rather than
				// being swallowed here and converted into a failure.
				throw e;
			} catch (Exception e) {
				log.error("[{}] page {} error: {}\nquery: {}", testName, pageNumber, e.getMessage(), query);
				fail(testName + " page " + pageNumber + ":\n" + query + "\n\n" + e.getMessage());
			}
		}

		log.info("[{}] pagination complete — {} pages fetched", testName, totalPages);
	}
}
