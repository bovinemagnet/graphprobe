package com.snowedunderproductions.graphprobe.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

/**
 * Provides test arguments dynamically to a JUnit&nbsp;5 {@code @ParameterizedTest}, selecting
 * the data source at runtime between a classpath CSV file and a programmatic
 * {@link org.junit.jupiter.params.provider.ArgumentsProvider}.
 *
 * <p>When the {@code USE_CSV} environment variable is set to {@code "true"} and a non-empty
 * {@link #csvResource()} is configured, arguments are read from that CSV file.  Otherwise the
 * annotation delegates to the class supplied via {@link #argumentsProvider()}, which is
 * typically a subclass of
 * {@link com.snowedunderproductions.graphprobe.database.BaseArgumentsProvider}.
 *
 * <p>The actual dispatch logic is implemented by {@link DynamicSourceProvider}, which is
 * registered as the {@link org.junit.jupiter.params.provider.ArgumentsSource} for this
 * annotation.
 *
 * <h3>Usage examples</h3>
 * <pre>{@code
 * // Database-driven test (no CSV fallback):
 * @ParameterizedTest
 * @DynamicSource(argumentsProvider = UserArgumentsProvider.class)
 * void testUsers(String id, String name, String email) {
 *     // test logic using database-provided data
 * }
 *
 * // With CSV fallback — uses CSV when USE_CSV=true, otherwise the DB provider:
 * @ParameterizedTest
 * @DynamicSource(
 *     csvResource       = "/test-data/users.csv",
 *     argumentsProvider = UserArgumentsProvider.class
 * )
 * void testUsersWithCsvFallback(String id, String name, String email) {
 *     // test logic
 * }
 * }</pre>
 *
 * @see DynamicSourceProvider
 * @see com.snowedunderproductions.graphprobe.database.BaseArgumentsProvider
 */
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@ArgumentsSource(DynamicSourceProvider.class)
public @interface DynamicSource {
    /**
     * Path to CSV resource file in classpath.
     *
     * @return the CSV resource path, or empty string if not using CSV
     */
    String csvResource() default "";

    /**
     * Delimiter character for CSV parsing.
     *
     * @return the delimiter character (default: ',')
     */
    char delimiter() default ',';

    /**
     * Number of header lines to skip when reading CSV.
     *
     * @return number of lines to skip (default: 1)
     */
    int linesToSkip() default 1;

    /**
     * Custom {@link org.junit.jupiter.params.provider.ArgumentsProvider} class that supplies
     * test arguments programmatically.  Implementations typically extend
     * {@link com.snowedunderproductions.graphprobe.database.BaseArgumentsProvider} to load data
     * from the shared database connection pool.
     *
     * @return the {@code ArgumentsProvider} implementation class; defaults to the base
     *         {@code ArgumentsProvider} interface (no-op unless overridden)
     */
    Class<? extends ArgumentsProvider> argumentsProvider() default ArgumentsProvider.class;
}
