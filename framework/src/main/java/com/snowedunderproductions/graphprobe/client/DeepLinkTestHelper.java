package com.snowedunderproductions.graphprobe.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Step;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reusable helper for deep-link integration tests that validate nested and
 * resolved fields in a GraphQL JSON response.
 *
 * <p>Typically used alongside {@link SimpleGraphQLClient#simpleJsonCall(String, String)}
 * or {@link GraphQLTestExecutor#simpleJsonCall(String, String)}, which return a
 * pretty-printed JSON string representing the matched object. That string is passed
 * directly to the methods here for structural and value validation.
 *
 * <h3>Validation levels</h3>
 * <ul>
 *   <li><strong>Structural</strong> — field exists, nested arrays are non-empty</li>
 *   <li><strong>Value</strong> — a nested field's text value equals an expected string</li>
 * </ul>
 *
 * <h3>Strict and lenient flavours</h3>
 * <p>Mirroring {@link PaginationTestHelper}, every check comes in two variants:
 * <ul>
 *   <li>{@code assert*} methods — <strong>fail</strong> the test when validation fails</li>
 *   <li>{@code assume*} methods — <strong>skip</strong> the test when validation fails,
 *       useful when nested data may not be populated in every environment</li>
 * </ul>
 *
 * <h3>Path syntax</h3>
 * <p>Paths use {@code /} as a separator, e.g. {@code "campusDetails/campusCode"}.
 * When an array node is encountered mid-path the first element ({@code [0]}) is
 * automatically descended into, mirroring the indexed-access pattern used throughout
 * the framework.
 *
 * @see PaginationTestHelper
 * @author Paul Snow
 * @since 0.0.0
 */
public final class DeepLinkTestHelper {

	private static final Logger log = LoggerFactory.getLogger(DeepLinkTestHelper.class);
	private static final ObjectMapper mapper = new ObjectMapper();

	private DeepLinkTestHelper() {
		// utility class
	}

	// ── Core: parse JSON ────────────────────────────────────────────────

	/**
	 * Parses a pretty-printed JSON string into a {@link JsonNode}.
	 * Fails the test if the input is null, empty, or invalid JSON.
	 *
	 * @param json    the JSON string (typically from
	 *                a GraphQL JSON response)
	 * @param context test name or description for assertion messages
	 * @return the parsed {@link JsonNode}
	 */
	@Step("Parse JSON response [{1}]")
	public static JsonNode parseJson(String json, String context) {
		return parseJson(json, context, null);
	}

	/**
	 * Parses a pretty-printed JSON string into a {@link JsonNode}.
	 * Fails the test if the input is null, empty, or invalid JSON.
	 * When a {@code query} is supplied it is included in the failure message
	 * so the offending GraphQL request is visible in test output.
	 *
	 * @param json    the JSON string (typically from
	 *                a GraphQL JSON response)
	 * @param context test name or description for assertion messages
	 * @param query   the serialised GraphQL query (may be {@code null})
	 * @return the parsed {@link JsonNode}
	 */
	@Step("Parse JSON response [{1}]")
	public static @Nullable JsonNode parseJson(String json, String context, String query) {
		if (json == null || json.isEmpty()) {
			String msg = context + ": JSON response is null or empty";
			if (query != null) {
				msg += "\nQuery: " + query;
			}
			fail(msg);
		}
		try {
			JsonNode node = mapper.readTree(json);
			log.debug("[{}] parsed JSON successfully", context);
			return node;
		} catch (Exception e) {
			String msg = context + ": failed to parse JSON: " + e.getMessage();
			if (query != null) {
				msg += "\nQuery: " + query;
			}
			fail(msg);
			return null; // unreachable
		}
	}

	// ── Path resolution ─────────────────────────────────────────────────

	/**
	 * Navigates a {@code /}-separated path through the JSON tree.
	 * Automatically descends into the first element of arrays encountered
	 * mid-path (mirroring the {@code [0]} indexing pattern used elsewhere).
	 *
	 * @param root the root node to navigate from
	 * @param path {@code /}-separated path, e.g. {@code "assignedLocations/location/building"}
	 * @return the resolved node (may be a missing node if path is invalid)
	 */
	private static JsonNode resolvePath(JsonNode root, String path) {
		JsonNode current = root;
		for (String segment : path.split("/")) {
			if (current.isArray() && current.size() > 0) {
				current = current.get(0);
			}
			current = current.path(segment);
		}
		return current;
	}

	// ── Structural assertions ───────────────────────────────────────────

	/**
	 * Asserts that a field exists (is not null or missing) at the given path.
	 *
	 * @param root    the root JSON node
	 * @param path    {@code /}-separated path, e.g. {@code "campusDetails/campusCode"}
	 * @param context test name for assertion messages
	 */
	@Step("Assert field exists: {1}")
	public static void assertFieldExists(JsonNode root, String path, String context) {
		JsonNode resolved = resolvePath(root, path);
		assertThat(resolved.isMissingNode() || resolved.isNull()).as("%s: field '%s' should exist", context, path).isFalse();
		log.debug("[{}] field '{}' exists", context, path);
	}

	/**
	 * Checks that a field exists at the given path.
	 * <strong>Skips</strong> the test if the field is missing or null
	 * (useful when nested data may not be resolved in every environment).
	 *
	 * @param root    the root JSON node
	 * @param path    {@code /}-separated path, e.g. {@code "assignedLocations/location/locationCode"}
	 * @param context test name for assertion messages
	 */
	@Step("Assume field exists: {1}")
	public static void assumeFieldExists(JsonNode root, String path, String context) {
		JsonNode resolved = resolvePath(root, path);
		if (resolved.isMissingNode() || resolved.isNull()) {
			log.info("[{}] field '{}' is missing or null — skipping", context, path);
			assumeTrue(false, context + ": field '" + path + "' is missing or null — skipping");
		}
		log.debug("[{}] field '{}' exists", context, path);
	}

	/**
	 * Asserts that multiple fields exist. Convenience for checking several
	 * nested field paths in one call.
	 *
	 * @param root    the root JSON node
	 * @param context test name for assertion messages
	 * @param paths   {@code /}-separated paths to validate
	 */
	@Step("Assert fields exist [{1}]")
	public static void assertFieldsExist(JsonNode root, String context, String... paths) {
		for (String path : paths) {
			assertFieldExists(root, path, context);
		}
	}

	/**
	 * Asserts that a field at the given path is an array and is non-empty.
	 * <strong>Fails</strong> the test if the array is missing or empty.
	 *
	 * @param root    the root JSON node
	 * @param path    {@code /}-separated path to the array field
	 * @param context test name for assertion messages
	 */
	@Step("Assert array not empty: {1}")
	public static void assertArrayNotEmpty(JsonNode root, String path, String context) {
		JsonNode resolved = resolvePath(root, path);
		assertThat(resolved.isMissingNode() || resolved.isNull()).as("%s: array field '%s' should exist", context, path).isFalse();
		assertThat(resolved.isArray()).as("%s: field '%s' should be an array", context, path).isTrue();
		assertThat(resolved.size()).as("%s: array '%s' should not be empty", context, path).isGreaterThan(0);
		log.debug("[{}] array '{}' has {} elements", context, path, resolved.size());
	}

	/**
	 * Checks that a field at the given path is an array and is non-empty.
	 * <strong>Skips</strong> the test if the array is missing or empty
	 * (useful when nested data may not be available in every environment).
	 *
	 * @param root    the root JSON node
	 * @param path    {@code /}-separated path to the array field
	 * @param context test name for assertion messages
	 */
	@Step("Assume array not empty: {1}")
	public static void assumeArrayNotEmpty(JsonNode root, String path, String context) {
		JsonNode resolved = resolvePath(root, path);
		if (resolved.isMissingNode() || resolved.isNull() || !resolved.isArray() || resolved.size() == 0) {
			log.info("[{}] array '{}' is missing or empty — skipping", context, path);
			assumeTrue(false, context + ": array '" + path + "' is missing or empty — skipping");
		}
		log.debug("[{}] array '{}' has {} elements", context, path, resolved.size());
	}

	// ── Value assertions ────────────────────────────────────────────────

	/**
	 * Asserts that the text value at the given path equals the expected value.
	 * <strong>Skips</strong> the test if {@code expectedValue} is {@code null}
	 * (allows graceful handling when the DB did not supply a comparison value).
	 *
	 * @param root          the root JSON node
	 * @param path          {@code /}-separated path to the field
	 * @param expectedValue the expected text value, or {@code null} to skip
	 * @param context       test name for assertion messages
	 */
	@Step("Assert field {1} equals {2}")
	public static void assertFieldEquals(JsonNode root, String path, String expectedValue, String context) {
		assumeThat(expectedValue).as("%s: expected value for '%s' not supplied — skipping", context, path).isNotNull();

		JsonNode resolved = resolvePath(root, path);
		assertThat(resolved.isMissingNode() || resolved.isNull()).as("%s: field '%s' should exist for value comparison", context, path).isFalse();
		assertThat(resolved.asText()).as("%s: field '%s' should equal '%s'", context, path, expectedValue).isEqualTo(expectedValue);
		log.debug("[{}] field '{}' equals '{}'", context, path, expectedValue);
	}

	/**
	 * Compares the text value at the given path with the expected value, but only
	 * if both the expected value is non-null and the field exists in the response.
	 * Skips silently if the field is missing or null in the response.
	 *
	 * @param root          the root JSON node
	 * @param path          {@code /}-separated path to the field
	 * @param expectedValue the expected text value, or {@code null} to skip
	 * @param context       test name for assertion messages
	 */
	@Step("Assert field {1} equals {2} if present")
	public static void assertFieldEqualsIfPresent(JsonNode root, String path, String expectedValue, String context) {
		if (expectedValue == null) {
			log.debug("[{}] expected value for '{}' is null — skipping", context, path);
			return;
		}

		JsonNode resolved = resolvePath(root, path);
		if (resolved.isMissingNode() || resolved.isNull()) {
			log.debug("[{}] field '{}' is missing or null in response — skipping comparison", context, path);
			return;
		}

		assertThat(resolved.asText()).as("%s: field '%s' should equal '%s'", context, path, expectedValue).isEqualTo(expectedValue);
		log.debug("[{}] field '{}' equals '{}'", context, path, expectedValue);
	}

	/**
	 * Asserts that an array at the given path contains at least one element
	 * where the specified child field matches the expected value.
	 *
	 * @param root          the root JSON node
	 * @param arrayPath     {@code /}-separated path to the array
	 * @param childField    the field name within each array element to check
	 * @param expectedValue the value to find
	 * @param context       test name for assertion messages
	 */
	@Step("Assert array {1} contains {2}={3}")
	public static void assertArrayContainsValue(JsonNode root, String arrayPath, String childField, String expectedValue, String context) {
		JsonNode resolved = resolvePath(root, arrayPath);
		assertThat(resolved.isArray()).as("%s: field '%s' should be an array", context, arrayPath).isTrue();

		boolean found = false;
		for (JsonNode element : resolved) {
			JsonNode child = element.path(childField);
			if (!child.isMissingNode() && expectedValue.equals(child.asText())) {
				found = true;
				break;
			}
		}
		assertThat(found).as("%s: array '%s' should contain element with %s='%s'", context, arrayPath, childField, expectedValue).isTrue();
		log.debug("[{}] array '{}' contains {}='{}'", context, arrayPath, childField, expectedValue);
	}

	// ── Array search ───────────────────────────────────────────────────

	/**
	 * Searches a JSON array for the first element where all specified fields
	 * match the expected values. This is useful when a GraphQL query returns
	 * multiple records and the test needs to find a specific one by key fields.
	 * <p>
	 * <strong>Skips</strong> the test if the array is empty;
	 * <strong>fails</strong> the test if no matching element is found.
	 *
	 * @param arrayJson   the JSON string representing an array of objects
	 * @param context     test name for assertion messages
	 * @param fieldChecks pairs of {@code [fieldName, expectedValue, ...]}
	 * @return the first matching {@link JsonNode}
	 */
	@Step("Find node by fields [{1}]")
	public static JsonNode findNodeByFields(String arrayJson, String context, String... fieldChecks) {
		if (fieldChecks.length % 2 != 0) {
			fail(context + ": fieldChecks must be pairs of [fieldName, expectedValue]");
		}
		JsonNode arrayNode = parseJson(arrayJson, context);
		if (!arrayNode.isArray() || arrayNode.size() == 0) {
			log.info("[{}] array is missing or empty — skipping", context);
			assumeTrue(false, context + ": expected a non-empty JSON array");
		}
		for (JsonNode element : arrayNode) {
			boolean allMatch = true;
			for (int i = 0; i < fieldChecks.length; i += 2) {
				JsonNode field = element.path(fieldChecks[i]);
				if (field.isMissingNode() || !fieldChecks[i + 1].equals(field.asText())) {
					allMatch = false;
					break;
				}
			}
			if (allMatch) {
				log.debug("[{}] found matching node in array", context);
				return element;
			}
		}
		fail(context + ": no element in array matched the specified fields");
		return null; // unreachable
	}

	// ── Convenience: full deep-link assertion ───────────────────────────

	/**
	 * Performs a standard deep-link assertion on a
	 * a GraphQL JSON response result.
	 * <strong>Fails</strong> the test if the nested field is missing or (for arrays) empty.
	 * <ol>
	 *   <li>Parses the JSON</li>
	 *   <li>Asserts root identifier fields match expected values (pairs in {@code idFieldChecks})</li>
	 *   <li>Asserts the nested field path exists</li>
	 *   <li>If the nested path is an array, asserts it is non-empty</li>
	 * </ol>
	 *
	 * @param jsonResult      the JSON string from {@code simpleJsonCall}
	 * @param context         test name for logging and assertion messages
	 * @param nestedFieldPath the nested field to validate
	 * @param idFieldChecks   pairs of {@code [fieldPath, expectedValue, ...]}
	 */
	@Step("Assert deep link {2} [{1}]")
	public static void assertDeepLink(String jsonResult, String context, String nestedFieldPath, String... idFieldChecks) {
		doDeepLink(jsonResult, context, nestedFieldPath, false, idFieldChecks);
	}

	/**
	 * Performs a standard deep-link check on a
	 * a GraphQL JSON response result.
	 * <strong>Skips</strong> the test if the nested field is missing or (for arrays) empty
	 * (useful when nested data may not be available in every environment).
	 *
	 * @param jsonResult      the JSON string from {@code simpleJsonCall}
	 * @param context         test name for logging and assertion messages
	 * @param nestedFieldPath the nested field to validate
	 * @param idFieldChecks   pairs of {@code [fieldPath, expectedValue, ...]}
	 * @see #assertDeepLink
	 */
	@Step("Assume deep link {2} [{1}]")
	public static void assumeDeepLink(String jsonResult, String context, String nestedFieldPath, String... idFieldChecks) {
		doDeepLink(jsonResult, context, nestedFieldPath, true, idFieldChecks);
	}

	// ── shared deep-link logic ──────────────────────────────────────────

	/**
	 * Shared implementation for {@link #assertDeepLink} and {@link #assumeDeepLink}.
	 * Parses {@code jsonResult}, validates root identifier field pairs, then checks
	 * the nested field exists and (if an array) is non-empty.
	 *
	 * @param jsonResult        the JSON string to validate
	 * @param context           test name for assertion messages
	 * @param nestedFieldPath   {@code /}-separated path to the nested field to check
	 * @param assumeNoResults   {@code true} to skip when the nested field is missing
	 *                          or empty; {@code false} to fail
	 * @param idFieldChecks     pairs of {@code [fieldPath, expectedValue, ...]} applied
	 *                          as root-level equality assertions
	 */
	private static void doDeepLink(String jsonResult, String context, String nestedFieldPath, boolean assumeNoResults, String... idFieldChecks) {
		JsonNode node = parseJson(jsonResult, context);

		// Validate root identifier fields (pairs: fieldPath, expectedValue)
		if (idFieldChecks.length % 2 != 0) {
			fail(context + ": idFieldChecks must be pairs of [fieldPath, expectedValue]");
		}
		for (int i = 0; i < idFieldChecks.length; i += 2) {
			assertFieldEquals(node, idFieldChecks[i], idFieldChecks[i + 1], context);
		}

		// Validate the nested field
		JsonNode nested = resolvePath(node, nestedFieldPath);
		if (nested.isMissingNode() || nested.isNull()) {
			if (assumeNoResults) {
				log.info("[{}] nested field '{}' is missing — skipping", context, nestedFieldPath);
				assumeTrue(false, context + ": nested field '" + nestedFieldPath + "' is missing — skipping");
			} else {
				fail(context + ": nested field '" + nestedFieldPath + "' should exist");
			}
		}

		if (nested.isArray()) {
			if (nested.size() == 0) {
				if (assumeNoResults) {
					log.info("[{}] nested array '{}' is empty — skipping", context, nestedFieldPath);
					assumeTrue(false, context + ": nested array '" + nestedFieldPath + "' is empty — skipping");
				} else {
					fail(context + ": nested array '" + nestedFieldPath + "' should not be empty");
				}
			}
			log.debug("[{}] nested array '{}' has {} elements", context, nestedFieldPath, nested.size());
		} else {
			log.debug("[{}] nested field '{}' exists (object/value)", context, nestedFieldPath);
		}
	}
}
