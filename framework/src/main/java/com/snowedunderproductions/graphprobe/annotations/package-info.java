/**
 * Custom JUnit&nbsp;5 annotations for data-driven and version-conditional test execution.
 *
 * <p>This package provides two complementary sets of annotations:
 *
 * <h3>Data-driven test annotations</h3>
 * <ul>
 *   <li>{@link com.snowedunderproductions.graphprobe.annotations.DynamicSource} — a
 *       meta-annotation that feeds parameterised tests from either a classpath CSV file or a
 *       custom {@link org.junit.jupiter.params.provider.ArgumentsProvider} implementation.
 *       The active source is selected at runtime via the {@code USE_CSV} environment variable.</li>
 *   <li>{@link com.snowedunderproductions.graphprobe.annotations.DynamicSourceProvider} — the
 *       {@link org.junit.jupiter.params.provider.ArgumentsProvider} registered by
 *       {@code @DynamicSource}.  It reads the annotation attributes, decides whether to load CSV
 *       lines or to delegate to the configured provider (typically a subclass of
 *       {@link com.snowedunderproductions.graphprobe.database.BaseArgumentsProvider}), and
 *       streams the resulting {@link org.junit.jupiter.params.provider.Arguments} to the JUnit
 *       engine.</li>
 * </ul>
 *
 * <h3>Version-conditional test annotations</h3>
 * <ul>
 *   <li>{@link com.snowedunderproductions.graphprobe.annotations.EnabledForVersion} — marks a
 *       test method or class so that it is only executed when the system version (supplied via
 *       the {@code -DcurrentVersion} JVM property) meets a semantic-version minimum.</li>
 *   <li>{@link com.snowedunderproductions.graphprobe.annotations.EnabledForVersionCondition} —
 *       the {@link org.junit.jupiter.api.extension.ExecutionCondition} that backs
 *       {@code @EnabledForVersion}.  It parses both the required minimum and the running version
 *       using the Java Semantic Versioning library and disables the test when the constraint is
 *       not satisfied.</li>
 * </ul>
 *
 * <h3>Typical usage</h3>
 * <pre>{@code
 * // Database-driven parameterised test (falls back to CSV when USE_CSV=true):
 * @ParameterizedTest
 * @DynamicSource(
 *     csvResource  = "/test-data/users.csv",
 *     argumentsProvider = UserArgumentsProvider.class
 * )
 * void testUsers(String id, String name) { ... }
 *
 * // Version-conditional test — skipped unless currentVersion >= 2.1.0:
 * @Test
 * @EnabledForVersion(minimumVersion = "2.1.0")
 * void testNewFeature() { ... }
 * }</pre>
 *
 * @see com.snowedunderproductions.graphprobe.database.BaseArgumentsProvider
 */
package com.snowedunderproductions.graphprobe.annotations;
