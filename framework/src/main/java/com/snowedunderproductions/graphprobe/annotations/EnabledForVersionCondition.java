package com.snowedunderproductions.graphprobe.annotations;

import com.github.zafarkhaja.semver.Version;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Optional;

/**
 * {@link org.junit.jupiter.api.extension.ExecutionCondition} that enables or disables a test
 * based on the version constraint declared by {@link EnabledForVersion}.
 *
 * <p>This class is registered automatically when {@code @EnabledForVersion} is present; it
 * should not be referenced directly in test code.
 *
 * <p>The evaluation logic is as follows:
 * <ul>
 *   <li>If no {@link EnabledForVersion} annotation is found on the element, the test is
 *       unconditionally enabled.</li>
 *   <li>If both {@link EnabledForVersion#version()} and {@link EnabledForVersion#minimumVersion()}
 *       are empty, the test is enabled for all versions.</li>
 *   <li>If only {@link EnabledForVersion#version()} is supplied, it is treated as the minimum
 *       version.</li>
 *   <li>The current system version is read from the {@code currentVersion} JVM system property
 *       (e.g., {@code -DcurrentVersion=1.4.0}).  When absent it defaults to {@code "0.0.0"}.</li>
 *   <li>If either version string is not a valid Semantic Version, the test is disabled with a
 *       descriptive error message rather than throwing an exception.</li>
 * </ul>
 *
 * <p>Version comparison follows Semantic Versioning rules as implemented by the
 * {@code java-semver} library.
 *
 * @see EnabledForVersion
 */
public class EnabledForVersionCondition implements ExecutionCondition {

    private static final ConditionEvaluationResult ENABLED =
        ConditionEvaluationResult.enabled("Version condition met");

    /**
     * Evaluates whether the test should be enabled by comparing the system version against the
     * minimum version declared by {@link EnabledForVersion}.
     *
     * <p>The current system version is obtained from the {@code currentVersion} JVM system
     * property; it defaults to {@code "0.0.0"} when not set.  If the annotation is absent on
     * the test element the test is unconditionally enabled.
     *
     * @param context the {@link ExtensionContext} for the test element being evaluated
     * @return a {@link ConditionEvaluationResult} indicating whether the test is enabled,
     *         together with a human-readable reason
     */
    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(
        ExtensionContext context
    ) {
        EnabledForVersion annotation = context
            .getElement()
            .flatMap(el ->
                Optional.ofNullable(el.getAnnotation(EnabledForVersion.class))
            )
            .orElse(null);

        // If no annotation is present, enable the test.
        if (annotation == null) {
            return ENABLED;
        }

        // Extract parameters from the annotation.
        String version = annotation.version();
        String minimumVersion = annotation.minimumVersion();

        // If neither parameter is supplied, run the test for all versions.
        if (version.isEmpty() && minimumVersion.isEmpty()) {
            return ENABLED;
        }

        // If only version is supplied, treat it as the minimum version.
        if (minimumVersion.isEmpty() && !version.isEmpty()) {
            minimumVersion = version;
        }

        // Retrieve the current version from system properties.
        // Ensure that you pass this property (e.g., -DcurrentVersion=1.4.0) via Gradle.
        String currentVersion = System.getProperty("currentVersion", "0.0.0");

        try {
            // Use Java SemVer to parse and compare versions.
            Version current = Version.parse(currentVersion);
            Version requiredMinimum = Version.parse(minimumVersion);

            if (current.compareTo(requiredMinimum) >= 0) {
                return ENABLED;
            } else {
                return ConditionEvaluationResult.disabled(
                    "Current version " +
                    current +
                    " is less than the required minimum version " +
                    requiredMinimum
                );
            }
        } catch (Exception e) {
            // If the version strings are invalid, disable the test with a meaningful message.
            return ConditionEvaluationResult.disabled(
                "Invalid version format: " + e.getMessage()
            );
        }
    }
}
