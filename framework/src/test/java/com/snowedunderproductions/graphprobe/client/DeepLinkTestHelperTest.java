package com.snowedunderproductions.graphprobe.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

/**
 * Unit tests for {@link DeepLinkTestHelper}.
 *
 * @author Paul Snow
 * @since 0.0.0
 */
@Epic("GraphQL Integration Tests")
@Feature("Test Utilities")
@Owner("Paul Snow")
class DeepLinkTestHelperTest {

	// Sample JSON mimicking a GraphQL activity response with nested fields
	private static final String ACTIVITY_JSON = """
		{
		  "subjectCode": "COMP101",
		  "activityGroupCode": "LEC",
		  "activityCode": "LEC01",
		  "allocated": [
		    {
		      "studentCode": "S001",
		      "subjectCode": "COMP101",
		      "activityGroupCode": "LEC",
		      "activityCode": "LEC01"
		    },
		    {
		      "studentCode": "S002",
		      "subjectCode": "COMP101",
		      "activityGroupCode": "LEC",
		      "activityCode": "LEC01"
		    }
		  ],
		  "campusDetails": {
		    "campusCode": "CLAY",
		    "description": "Clayton",
		    "derivedTimezone": "Australia/Melbourne"
		  },
		  "assignedLocations": [
		    {
		      "locationCode": "LOC01",
		      "location": {
		        "building": "Building A",
		        "campus": "CLAY",
		        "capacity": 200
		      }
		    }
		  ],
		  "constraints": [],
		  "eventDates": [
		    {
		      "eventStartDateTime": "2026-03-01T09:00:00",
		      "eventEndDateTime": "2026-03-01T11:00:00"
		    }
		  ]
		}
		""";

	private static final String EMPTY_ARRAY_JSON = """
		{
		  "subjectCode": "COMP101",
		  "allocated": []
		}
		""";

	@Nested
	@DisplayName("parseJson")
	class ParseJsonTests {

		@Story("JSON parsing utility")
		@Description("Parses valid JSON into a JsonNode and verifies field access")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("parses valid JSON into a JsonNode")
		void parsesValidJson() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertThat(node).isNotNull();
			assertThat(node.path("subjectCode").asText()).isEqualTo("COMP101");
		}

		@Story("JSON parsing utility")
		@Description("Fails with an AssertionError when parseJson is called with null input")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("fails on null input")
		void failsOnNull() {
			assertThatThrownBy(() -> DeepLinkTestHelper.parseJson(null, "test")).isInstanceOf(AssertionError.class);
		}

		@Story("JSON parsing utility")
		@Description("Fails with an AssertionError when parseJson is called with an empty string")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("fails on empty string")
		void failsOnEmpty() {
			assertThatThrownBy(() -> DeepLinkTestHelper.parseJson("", "test")).isInstanceOf(AssertionError.class);
		}

		@Story("JSON parsing utility")
		@Description("Fails with an AssertionError when parseJson is called with malformed JSON")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("fails on invalid JSON")
		void failsOnInvalidJson() {
			assertThatThrownBy(() -> DeepLinkTestHelper.parseJson("{not json", "test")).isInstanceOf(AssertionError.class);
		}
	}

	@Nested
	@DisplayName("assertFieldExists")
	class AssertFieldExistsTests {

		@Story("Deep link field assertion")
		@Description("Passes without error when assertFieldExists is called for an existing top-level field")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("passes for top-level field")
		void passesForTopLevelField() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertDoesNotThrow(() -> DeepLinkTestHelper.assertFieldExists(node, "subjectCode", "test"));
		}

		@Story("Deep link field assertion")
		@Description("Passes without error when assertFieldExists is called for an existing nested object field using path notation")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("passes for nested object field")
		void passesForNestedField() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertDoesNotThrow(() -> DeepLinkTestHelper.assertFieldExists(node, "campusDetails/campusCode", "test"));
		}

		@Story("Deep link field assertion")
		@Description("Passes without error when assertFieldExists auto-descends into an array to resolve a deeply nested field")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("passes for deeply nested field through array auto-descend")
		void passesForDeeplyNestedField() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertDoesNotThrow(() -> DeepLinkTestHelper.assertFieldExists(node, "assignedLocations/location/building", "test"));
		}

		@Story("Deep link field assertion")
		@Description("Fails with an AssertionError when assertFieldExists is called for a field that does not exist")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("fails for missing field")
		void failsForMissingField() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertThatThrownBy(() -> DeepLinkTestHelper.assertFieldExists(node, "nonExistentField", "test")).isInstanceOf(AssertionError.class);
		}

		@Story("Deep link field assertion")
		@Description("Fails with an AssertionError when assertFieldExists is called for a missing nested field path")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("fails for missing nested field")
		void failsForMissingNestedField() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertThatThrownBy(() -> DeepLinkTestHelper.assertFieldExists(node, "campusDetails/nonExistent", "test")).isInstanceOf(AssertionError.class);
		}
	}

	@Nested
	@DisplayName("assertFieldsExist")
	class AssertFieldsExistTests {

		@Story("Deep link multiple field assertion")
		@Description("Passes without error when assertFieldsExist is called and all specified fields exist")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("passes when all fields exist")
		void passesWhenAllExist() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertDoesNotThrow(() -> DeepLinkTestHelper.assertFieldsExist(node, "test", "subjectCode", "activityCode", "campusDetails/campusCode"));
		}

		@Story("Deep link multiple field assertion")
		@Description("Fails with an AssertionError when assertFieldsExist is called and any one field is missing")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("fails when any field is missing")
		void failsWhenAnyMissing() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertThatThrownBy(() -> DeepLinkTestHelper.assertFieldsExist(node, "test", "subjectCode", "missingField")).isInstanceOf(AssertionError.class);
		}
	}

	@Nested
	@DisplayName("assertArrayNotEmpty")
	class AssertArrayNotEmptyTests {

		@Story("Deep link array assertion")
		@Description("Passes without error when assertArrayNotEmpty is called on an array with elements")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("passes for non-empty array")
		void passesForNonEmptyArray() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertDoesNotThrow(() -> DeepLinkTestHelper.assertArrayNotEmpty(node, "allocated", "test"));
		}

		@Story("Deep link array assertion")
		@Description("Fails with an AssertionError when assertArrayNotEmpty is called on an empty array")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("fails for empty array")
		void failsForEmptyArray() {
			JsonNode node = DeepLinkTestHelper.parseJson(EMPTY_ARRAY_JSON, "test");
			assertThatThrownBy(() -> DeepLinkTestHelper.assertArrayNotEmpty(node, "allocated", "test")).isInstanceOf(AssertionError.class);
		}

		@Story("Deep link array assertion")
		@Description("Fails with an AssertionError when assertArrayNotEmpty is called for an array field that does not exist")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("fails for missing field")
		void failsForMissingField() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertThatThrownBy(() -> DeepLinkTestHelper.assertArrayNotEmpty(node, "nonExistent", "test")).isInstanceOf(AssertionError.class);
		}
	}

	@Nested
	@DisplayName("assumeArrayNotEmpty")
	class AssumeArrayNotEmptyTests {

		@Story("Deep link array assumption")
		@Description("Passes without error when assumeArrayNotEmpty is called on an array with elements")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("passes for non-empty array")
		void passesForNonEmptyArray() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertDoesNotThrow(() -> DeepLinkTestHelper.assumeArrayNotEmpty(node, "allocated", "test"));
		}

		@Story("Deep link array assumption")
		@Description("Aborts the test via TestAbortedException when assumeArrayNotEmpty is called on an empty array")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("skips for empty array")
		void skipsForEmptyArray() {
			JsonNode node = DeepLinkTestHelper.parseJson(EMPTY_ARRAY_JSON, "test");
			assertThatThrownBy(() -> DeepLinkTestHelper.assumeArrayNotEmpty(node, "allocated", "test")).isInstanceOf(TestAbortedException.class);
		}
	}

	@Nested
	@DisplayName("assertFieldEquals")
	class AssertFieldEqualsTests {

		@Story("Deep link field equality assertion")
		@Description("Passes without error when assertFieldEquals is called and the field value matches the expected value")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("passes when value matches")
		void passesWhenMatches() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertDoesNotThrow(() -> DeepLinkTestHelper.assertFieldEquals(node, "subjectCode", "COMP101", "test"));
		}

		@Story("Deep link field equality assertion")
		@Description("Passes without error when assertFieldEquals is called for a nested field path and the value matches")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("passes for nested field value")
		void passesForNestedValue() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertDoesNotThrow(() -> DeepLinkTestHelper.assertFieldEquals(node, "campusDetails/campusCode", "CLAY", "test"));
		}

		@Story("Deep link field equality assertion")
		@Description("Fails with an AssertionError when assertFieldEquals is called and the field value does not match the expected value")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("fails when value does not match")
		void failsWhenMismatch() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertThatThrownBy(() -> DeepLinkTestHelper.assertFieldEquals(node, "subjectCode", "WRONG", "test")).isInstanceOf(AssertionError.class);
		}

		@Story("Deep link field equality assertion")
		@Description("Aborts the test via TestAbortedException when assertFieldEquals is called with a null expected value")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("skips when expected value is null")
		void skipsWhenExpectedNull() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertThatThrownBy(() -> DeepLinkTestHelper.assertFieldEquals(node, "subjectCode", null, "test")).isInstanceOf(TestAbortedException.class);
		}
	}

	@Nested
	@DisplayName("assertArrayContainsValue")
	class AssertArrayContainsValueTests {

		@Story("Deep link array value search")
		@Description("Passes without error when assertArrayContainsValue finds a matching element in the array")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("passes when array contains matching element")
		void passesWhenContains() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertDoesNotThrow(() -> DeepLinkTestHelper.assertArrayContainsValue(node, "allocated", "studentCode", "S001", "test"));
		}

		@Story("Deep link array value search")
		@Description("Passes without error when assertArrayContainsValue matches the second element in the array")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("passes for second element match")
		void passesForSecondElement() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertDoesNotThrow(() -> DeepLinkTestHelper.assertArrayContainsValue(node, "allocated", "studentCode", "S002", "test"));
		}

		@Story("Deep link array value search")
		@Description("Fails with an AssertionError when assertArrayContainsValue finds no matching element in the array")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("fails when no element matches")
		void failsWhenNoMatch() {
			JsonNode node = DeepLinkTestHelper.parseJson(ACTIVITY_JSON, "test");
			assertThatThrownBy(() -> DeepLinkTestHelper.assertArrayContainsValue(node, "allocated", "studentCode", "MISSING", "test")).isInstanceOf(AssertionError.class);
		}
	}

	@Nested
	@DisplayName("assertDeepLink")
	class AssertDeepLinkTests {

		@Story("Deep link combined assertion")
		@Description("Validates root id fields and confirms the nested array is not empty using assertDeepLink")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("validates root ids and nested array field")
		void validatesRootAndNestedArray() {
			assertDoesNotThrow(() -> DeepLinkTestHelper.assertDeepLink(ACTIVITY_JSON, "test", "allocated", "subjectCode", "COMP101", "activityCode", "LEC01"));
		}

		@Story("Deep link combined assertion")
		@Description("Validates root id fields and confirms the nested object field exists using assertDeepLink")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("validates root ids and nested object field")
		void validatesRootAndNestedObject() {
			assertDoesNotThrow(() -> DeepLinkTestHelper.assertDeepLink(ACTIVITY_JSON, "test", "campusDetails/campusCode", "subjectCode", "COMP101"));
		}

		@Story("Deep link combined assertion")
		@Description("Fails with an AssertionError when assertDeepLink is called with an incorrect root id value")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("fails when root id value is wrong")
		void failsWhenRootIdWrong() {
			assertThatThrownBy(() -> DeepLinkTestHelper.assertDeepLink(ACTIVITY_JSON, "test", "allocated", "subjectCode", "WRONG")).isInstanceOf(AssertionError.class);
		}

		@Story("Deep link combined assertion")
		@Description("Fails with an AssertionError when assertDeepLink is called and the nested field resolves to an empty array")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("fails when nested field is empty array")
		void failsWhenNestedEmpty() {
			assertThatThrownBy(() -> DeepLinkTestHelper.assertDeepLink(EMPTY_ARRAY_JSON, "test", "allocated", "subjectCode", "COMP101")).isInstanceOf(AssertionError.class);
		}
	}

	@Nested
	@DisplayName("assumeDeepLink")
	class AssumeDeepLinkTests {

		@Story("Deep link combined assumption")
		@Description("Validates root id fields and confirms the nested array is not empty using assumeDeepLink")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("validates root ids and nested array field")
		void validatesRootAndNestedArray() {
			assertDoesNotThrow(() -> DeepLinkTestHelper.assumeDeepLink(ACTIVITY_JSON, "test", "allocated", "subjectCode", "COMP101", "activityCode", "LEC01"));
		}

		@Story("Deep link combined assumption")
		@Description("Aborts the test via TestAbortedException when assumeDeepLink is called and the nested field is an empty array")
		@Severity(SeverityLevel.NORMAL)
		@Test
		@DisplayName("skips when nested field is empty array")
		void skipsWhenNestedEmpty() {
			assertThatThrownBy(() -> DeepLinkTestHelper.assumeDeepLink(EMPTY_ARRAY_JSON, "test", "allocated", "subjectCode", "COMP101")).isInstanceOf(TestAbortedException.class);
		}
	}
}
