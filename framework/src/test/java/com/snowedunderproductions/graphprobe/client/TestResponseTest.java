package com.snowedunderproductions.graphprobe.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TestResponse}, focusing on the {@code containsError}/{@code containsData}
 * checks which must hold regardless of whether JVM assertions ({@code -ea}) are enabled.
 *
 * @author Paul Snow
 * @since 0.0.0
 */
class TestResponseTest {

    @Test
    void containsErrorReturnsTrueWhenErrorContainsExpectedText() {
        TestResponse response = new TestResponse(true, null, "User not found in directory");

        assertThat(response.containsError("not found")).isTrue();
    }

    @Test
    void containsErrorThrowsWhenExpectedTextAbsent() {
        TestResponse response = new TestResponse(true, null, "Some other error");

        assertThatThrownBy(() -> response.containsError("not found"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("was not found in the error");
    }

    @Test
    void containsErrorThrowsWhenThereIsNoError() {
        TestResponse response = new TestResponse(true, "data", null);

        assertThatThrownBy(() -> response.containsError("anything"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("no error data");
    }

    @Test
    void containsErrorRejectsBlankExpectation() {
        TestResponse response = new TestResponse(true, null, "boom");

        assertThatThrownBy(() -> response.containsError("  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("can't be blank");
    }

    @Test
    void containsDataReturnsTrueWhenDataContainsExpectedText() {
        TestResponse response = new TestResponse(true, "{\"user\":{\"name\":\"alice\"}}", null);

        assertThat(response.containsData("alice")).isTrue();
    }

    @Test
    void containsDataThrowsWhenExpectedTextAbsent() {
        TestResponse response = new TestResponse(true, "{\"user\":{\"name\":\"bob\"}}", null);

        assertThatThrownBy(() -> response.containsData("alice"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("was not found in the data");
    }

    @Test
    void containsDataThrowsWhenThereIsNoData() {
        TestResponse response = new TestResponse(false, null, "error");

        assertThatThrownBy(() -> response.containsData("anything"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("no data to search");
    }
}
