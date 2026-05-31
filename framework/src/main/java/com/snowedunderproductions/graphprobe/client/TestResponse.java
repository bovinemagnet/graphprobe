package com.snowedunderproductions.graphprobe.client;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable response wrapper for GraphQL test executions that may legitimately
 * return either data or a specific error.
 *
 * <p>Returned by
 * {@link GraphQLTestClient#executeQueryOrPossibleSpecificError(String, java.util.Optional, java.util.Optional)}
 * and
 * {@link SimpleGraphQLClient#executeQueryOrPossibleSpecificError(String, java.util.Optional, java.util.Optional)}.
 * The {@code success} flag encodes the caller's interpretation of whether the
 * response met expectations — it is {@code true} when the expected data was found
 * with no error, or when the expected error was present.
 *
 * <h3>Usage example</h3>
 * <pre>{@code
 * TestResponse response = client.executeQueryOrPossibleSpecificError(
 *     query,
 *     Optional.of("$.data.user"),
 *     Optional.of("User not found")
 * );
 *
 * if (response.dataOnlySuccess()) {
 *     String userData = response.data();
 * } else if (response.errorOnlySuccess()) {
 *     assertTrue(response.containsError("User not found"));
 * }
 * }</pre>
 *
 * @param success {@code true} when the response met the caller's expectations
 * @param data    the extracted response data (may be {@code null} or empty)
 * @param error   the first GraphQL error message (may be {@code null} or empty)
 *
 * @see GraphQLTestClient#executeQueryOrPossibleSpecificError(String, java.util.Optional, java.util.Optional)
 * @see SimpleGraphQLClient#executeQueryOrPossibleSpecificError(String, java.util.Optional, java.util.Optional)
 */
public record TestResponse(boolean success, String data, String error) {

    /**
     * Checks if the response was successful and contains only data (no error).
     *
     * @return {@code true} when flagged as success with data but no error
     */
    public boolean dataOnlySuccess() {
        return success && !hasError() && hasData();
    }

    /**
     * Checks if the response was successful and contains only an error (no data).
     * This is useful for testing expected error scenarios.
     *
     * @return {@code true} when flagged as success with error but no data
     */
    public boolean errorOnlySuccess() {
        return success && hasError() && !hasData();
    }

    /**
     * Returns {@code true} when the response carries a non-blank error message.
     *
     * @return {@code true} if {@link #error()} is non-{@code null} and non-blank;
     *         {@code false} otherwise
     */
    public boolean hasError() {
        return !(null == error || error.isBlank() || error.isEmpty());
    }

    /**
     * Returns {@code true} when the response carries a non-blank data payload.
     *
     * @return {@code true} if {@link #data()} is non-{@code null} and non-blank;
     *         {@code false} otherwise
     */
    public boolean hasData() {
        return !(null == data || data.isBlank() || data.isEmpty());
    }

    /**
     * Asserts that the expected error message is contained within the error response.
     *
     * @param expectedError the error message to check for (e.g., "must contain")
     * @return {@code true} if the expected error is found
     * @throws AssertionError if the expected error is not found or if there is no error data
     */
    public boolean containsError(@NotNull String expectedError) {
        if (expectedError.isBlank()) {
            throw new IllegalArgumentException("The `expectedError` can't be blank or empty");
        }
        if (!hasError()) {
            throw new AssertionError("The `expectedError` cannot be found as there is no error data");
        }
        if (!error().contains(expectedError)) {
            throw new AssertionError("The expectedError:\"" + expectedError +
                "\" was not found in the error: " + error().substring(0, Math.min(25, error().length())) + "...");
        }
        return true;
    }

    /**
     * Asserts that the expected data is contained within the data response.
     *
     * @param expectedData the data message to check for (e.g., "username")
     * @return {@code true} if the expected data is found
     * @throws AssertionError if the expected data is not found or if there is no data
     */
    public boolean containsData(@NotNull String expectedData) {
        if (expectedData.isBlank()) {
            throw new IllegalArgumentException("The `expectedData` can't be blank or empty");
        }
        if (!hasData()) {
            throw new AssertionError("The `expectedData` was not found as there is no data to search");
        }
        if (!data().contains(expectedData)) {
            throw new AssertionError("The expectedData:\"" + expectedData +
                "\" was not found in the data: " + data().substring(0, Math.min(25, data().length())) + "...");
        }
        return true;
    }

    @NotNull
    @Contract(pure = true)
    public String toString() {
        return "TestResponse: Success: " + success + " Data: " + data + " Error: " + error;
    }
}
