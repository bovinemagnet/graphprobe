package com.snowedunderproductions.graphprobe.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Conditionally enables a test method or test class when the running system version meets a
 * semantic-version minimum.
 *
 * <p>The condition is evaluated by {@link EnabledForVersionCondition}, which is registered
 * automatically via {@link org.junit.jupiter.api.extension.ExtendWith}.  The current system
 * version is read from the {@code currentVersion} JVM system property
 * (e.g., {@code -DcurrentVersion=1.4.0}); when the property is absent it defaults to
 * {@code "0.0.0"}.
 *
 * <p>Version comparison follows Semantic Versioning rules.  The annotation accepts two
 * equivalent ways to specify the minimum version:
 * <ul>
 *   <li>{@link #minimumVersion()} — the preferred, explicit attribute.</li>
 *   <li>{@link #version()} — treated as the minimum version when {@code minimumVersion} is
 *       not set.</li>
 * </ul>
 *
 * <p>If neither attribute is set, the test is unconditionally enabled.
 *
 * <h3>Usage examples</h3>
 * <pre>{@code
 * @Test
 * @EnabledForVersion(minimumVersion = "2.0.0")
 * void testNewFeature() {
 *     // runs only when currentVersion >= 2.0.0
 * }
 *
 * @Test
 * @EnabledForVersion(version = "1.5.0")
 * void testOlderFeature() {
 *     // runs when currentVersion >= 1.5.0
 * }
 * }</pre>
 *
 * @see EnabledForVersionCondition
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@ExtendWith(EnabledForVersionCondition.class)
public @interface EnabledForVersion {
    /**
     * Version requirement expressed as a semantic version string (e.g., {@code "1.3.4"}).
     * When {@link #minimumVersion()} is not set, this attribute is treated as the minimum
     * version.  Prefer {@link #minimumVersion()} for clarity.
     *
     * @return the version string, or an empty string if not set
     */
    String version() default "";

    /**
     * The minimum semantic version that the system must be at or above for the test to run
     * (e.g., {@code "2.0.0"}).  Takes precedence over {@link #version()} when both are set.
     *
     * @return the minimum version string, or an empty string if not set
     */
    String minimumVersion() default "";
}
