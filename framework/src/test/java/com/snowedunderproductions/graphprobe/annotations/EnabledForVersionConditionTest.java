package com.snowedunderproductions.graphprobe.annotations;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.AnnotatedElement;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * Unit tests for EnabledForVersionCondition version-based test execution logic.
 */
@DisplayName("EnabledForVersionCondition Tests")
class EnabledForVersionConditionTest {

    private EnabledForVersionCondition condition;

    @Mock
    private ExtensionContext context;

    @Mock
    private AnnotatedElement element;

    private String originalCurrentVersion;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        condition = new EnabledForVersionCondition();

        // Save original system property
        originalCurrentVersion = System.getProperty("currentVersion");

        // Setup basic mock behavior
        when(context.getElement()).thenReturn(Optional.of(element));
    }

    @AfterEach
    void tearDown() throws Exception {
        // Restore original system property
        if (originalCurrentVersion != null) {
            System.setProperty("currentVersion", originalCurrentVersion);
        } else {
            System.clearProperty("currentVersion");
        }

        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("Should enable test when no annotation is present")
    void testNoAnnotationEnablesTest() {
        when(element.getAnnotation(EnabledForVersion.class)).thenReturn(null);

        ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);

        assertThat(result.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Should enable test when both version parameters are empty")
    void testEmptyVersionParametersEnablesTest() {
        EnabledForVersion annotation = mock(EnabledForVersion.class);
        when(annotation.version()).thenReturn("");
        when(annotation.minimumVersion()).thenReturn("");
        when(element.getAnnotation(EnabledForVersion.class)).thenReturn(annotation);

        ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);

        assertThat(result.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Should enable test when current version meets minimum requirement")
    void testVersionMeetsMinimum() {
        System.setProperty("currentVersion", "2.0.0");

        EnabledForVersion annotation = mock(EnabledForVersion.class);
        when(annotation.version()).thenReturn("");
        when(annotation.minimumVersion()).thenReturn("1.0.0");
        when(element.getAnnotation(EnabledForVersion.class)).thenReturn(annotation);

        ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);

        assertThat(result.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Should disable test when current version is below minimum requirement")
    void testVersionBelowMinimum() {
        System.setProperty("currentVersion", "1.0.0");

        EnabledForVersion annotation = mock(EnabledForVersion.class);
        when(annotation.version()).thenReturn("");
        when(annotation.minimumVersion()).thenReturn("2.0.0");
        when(element.getAnnotation(EnabledForVersion.class)).thenReturn(annotation);

        ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);

        assertThat(result.isDisabled()).isTrue();
        assertThat(result.getReason()).isPresent();
        assertThat(result.getReason().get())
            .contains("less than the required minimum version");
    }

    @Test
    @DisplayName("Should enable test when current version equals minimum requirement")
    void testVersionEqualsMinimum() {
        System.setProperty("currentVersion", "1.5.0");

        EnabledForVersion annotation = mock(EnabledForVersion.class);
        when(annotation.version()).thenReturn("");
        when(annotation.minimumVersion()).thenReturn("1.5.0");
        when(element.getAnnotation(EnabledForVersion.class)).thenReturn(annotation);

        ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);

        assertThat(result.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Should use version as minimumVersion when minimumVersion is empty")
    void testVersionUsedAsMinimumWhenMinimumEmpty() {
        System.setProperty("currentVersion", "2.0.0");

        EnabledForVersion annotation = mock(EnabledForVersion.class);
        when(annotation.version()).thenReturn("1.0.0");
        when(annotation.minimumVersion()).thenReturn("");
        when(element.getAnnotation(EnabledForVersion.class)).thenReturn(annotation);

        ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);

        assertThat(result.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Should disable test with invalid current version format")
    void testInvalidCurrentVersionFormat() {
        System.setProperty("currentVersion", "invalid-version");

        EnabledForVersion annotation = mock(EnabledForVersion.class);
        when(annotation.version()).thenReturn("");
        when(annotation.minimumVersion()).thenReturn("1.0.0");
        when(element.getAnnotation(EnabledForVersion.class)).thenReturn(annotation);

        ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);

        assertThat(result.isDisabled()).isTrue();
        assertThat(result.getReason()).isPresent();
        assertThat(result.getReason().get()).contains("Invalid version format");
    }

    @Test
    @DisplayName("Should disable test with invalid minimum version format")
    void testInvalidMinimumVersionFormat() {
        System.setProperty("currentVersion", "1.0.0");

        EnabledForVersion annotation = mock(EnabledForVersion.class);
        when(annotation.version()).thenReturn("");
        when(annotation.minimumVersion()).thenReturn("not-a-version");
        when(element.getAnnotation(EnabledForVersion.class)).thenReturn(annotation);

        ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);

        assertThat(result.isDisabled()).isTrue();
        assertThat(result.getReason()).isPresent();
        assertThat(result.getReason().get()).contains("Invalid version format");
    }

    @Test
    @DisplayName("Should use default version 0.0.0 when currentVersion property not set")
    void testDefaultVersionWhenPropertyNotSet() {
        System.clearProperty("currentVersion");

        EnabledForVersion annotation = mock(EnabledForVersion.class);
        when(annotation.version()).thenReturn("");
        when(annotation.minimumVersion()).thenReturn("1.0.0");
        when(element.getAnnotation(EnabledForVersion.class)).thenReturn(annotation);

        ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);

        // 0.0.0 < 1.0.0, so test should be disabled
        assertThat(result.isDisabled()).isTrue();
    }

    @Test
    @DisplayName("Should handle semantic version with patch numbers")
    void testSemanticVersionWithPatch() {
        System.setProperty("currentVersion", "1.2.3");

        EnabledForVersion annotation = mock(EnabledForVersion.class);
        when(annotation.version()).thenReturn("");
        when(annotation.minimumVersion()).thenReturn("1.2.0");
        when(element.getAnnotation(EnabledForVersion.class)).thenReturn(annotation);

        ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);

        assertThat(result.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Should handle pre-release versions")
    void testPreReleaseVersions() {
        System.setProperty("currentVersion", "1.0.0-alpha");

        EnabledForVersion annotation = mock(EnabledForVersion.class);
        when(annotation.version()).thenReturn("");
        when(annotation.minimumVersion()).thenReturn("1.0.0-alpha");
        when(element.getAnnotation(EnabledForVersion.class)).thenReturn(annotation);

        ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);

        assertThat(result.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Should handle version comparisons with build metadata")
    void testVersionWithBuildMetadata() {
        System.setProperty("currentVersion", "1.0.0+20130313144700");

        EnabledForVersion annotation = mock(EnabledForVersion.class);
        when(annotation.version()).thenReturn("");
        when(annotation.minimumVersion()).thenReturn("1.0.0");
        when(element.getAnnotation(EnabledForVersion.class)).thenReturn(annotation);

        ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);

        assertThat(result.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Should handle context with no element")
    void testContextWithNoElement() {
        when(context.getElement()).thenReturn(Optional.empty());

        ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);

        // Without an element, no annotation can be found, so test should be enabled
        assertThat(result.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Should handle very high version numbers")
    void testVeryHighVersionNumbers() {
        System.setProperty("currentVersion", "999.999.999");

        EnabledForVersion annotation = mock(EnabledForVersion.class);
        when(annotation.version()).thenReturn("");
        when(annotation.minimumVersion()).thenReturn("1.0.0");
        when(element.getAnnotation(EnabledForVersion.class)).thenReturn(annotation);

        ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);

        assertThat(result.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("Should prioritize minimumVersion over version when both are set")
    void testMinimumVersionPrioritized() {
        System.setProperty("currentVersion", "1.5.0");

        EnabledForVersion annotation = mock(EnabledForVersion.class);
        when(annotation.version()).thenReturn("2.0.0");  // This should be ignored
        when(annotation.minimumVersion()).thenReturn("1.0.0");  // This should be used
        when(element.getAnnotation(EnabledForVersion.class)).thenReturn(annotation);

        ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);

        // 1.5.0 >= 1.0.0, so should be enabled
        assertThat(result.isDisabled()).isFalse();
    }
}
