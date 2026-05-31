/**
 * Database connection management and database-driven test-data providers for the
 * GraphProbe integration-test framework.
 *
 * <p>This package is responsible for two complementary concerns:
 * <ul>
 *   <li><strong>Connection pool lifecycle</strong> — creating, configuring, monitoring,
 *       and gracefully shutting down a HikariCP connection pool that is shared across
 *       all test providers in the same JVM.</li>
 *   <li><strong>Test argument provision</strong> — supplying rows from a PostgreSQL
 *       database as JUnit 5 {@code Arguments} streams, with Caffeine-backed caching so
 *       repeated parameterised-test runs do not re-query the database on every access.</li>
 * </ul>
 *
 * <h3>Core types</h3>
 * <ul>
 *   <li>{@link com.snowedunderproductions.graphprobe.database.HikariConfig} — static
 *       factory that creates and validates a {@code HikariDataSource} from environment
 *       variables, with PostgreSQL-specific performance tuning and optional SSL.</li>
 *   <li>{@link com.snowedunderproductions.graphprobe.database.DatabaseConnectionManager} —
 *       process-wide singleton that owns the pool and exposes type-safe
 *       {@code executeWithConnection} helpers; all providers share this single instance.</li>
 *   <li>{@link com.snowedunderproductions.graphprobe.database.BaseArgumentsProvider} —
 *       abstract template that subclasses extend to supply SQL, row-to-{@code Arguments}
 *       mapping, and a cache key; handles pool access and Caffeine caching automatically.
 *       Providers are referenced from the
 *       {@link com.snowedunderproductions.graphprobe.annotations.DynamicSource} annotation
 *       and dispatched by
 *       {@link com.snowedunderproductions.graphprobe.annotations.DynamicSourceProvider}.</li>
 *   <li>{@link com.snowedunderproductions.graphprobe.database.ConnectionPoolMonitor} —
 *       background scheduler that logs pool health, warns on high utilisation, and flags
 *       potential connection leaks at a configurable interval.</li>
 *   <li>{@link com.snowedunderproductions.graphprobe.database.HikariStatsListener} —
 *       a JUnit Platform {@link org.junit.platform.launcher.TestExecutionListener} that
 *       starts {@code ConnectionPoolMonitor} when a test plan begins and stops it when
 *       the plan finishes; opt-in via a service-loader registration file.</li>
 *   <li>{@link com.snowedunderproductions.graphprobe.database.DatabaseTestLifecycle} —
 *       a JUnit 5 {@link org.junit.jupiter.api.extension.Extension} that initialises the
 *       pool before the first test class that uses it, validates connectivity, and
 *       coordinates a graceful JVM-shutdown hook.</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <p>All configuration is read at runtime via
 * {@link com.snowedunderproductions.graphprobe.config.EnvConfig} from a {@code .env}
 * file or the process environment.  No values are hardcoded.  Required variables:
 * <ul>
 *   <li>{@code POSTGRES_URL} — JDBC connection URL.</li>
 *   <li>{@code POSTGRES_USER} — database username.</li>
 *   <li>{@code POSTGRES_PASSWORD} — database password.</li>
 * </ul>
 * Optional tuning variables include {@code HIKARI_MINIMUM_IDLE},
 * {@code HIKARI_MAXIMUM_POOL_SIZE}, {@code POSTGRES_SSL_MODE}, and
 * {@code HIKARI_STATS_INTERVAL_SECONDS}.
 *
 * <h3>Typical usage</h3>
 * <pre>{@code
 * // 1. Attach the lifecycle extension to a test class (or register it globally):
 * @ExtendWith(DatabaseTestLifecycle.class)
 * class MyGraphQLTests {
 *
 *     // 2. Reference a provider subclass from @DynamicSource:
 *     @ParameterizedTest
 *     @DynamicSource(argumentsProvider = MyArgumentsProvider.class)
 *     void testQuery(String id, String name) { ... }
 * }
 *
 * // 3. Implement the provider:
 * public class MyArgumentsProvider extends BaseArgumentsProvider {
 *     protected String getSQL()                           { return "SELECT id, name FROM items"; }
 *     protected String getCacheKey()                      { return "items"; }
 *     protected Arguments extractArguments(ResultSet rs) throws SQLException {
 *         return Arguments.of(getSafeString(rs, "id"), getSafeString(rs, "name"));
 *     }
 * }
 * }</pre>
 *
 * @see com.snowedunderproductions.graphprobe.config
 * @see com.snowedunderproductions.graphprobe.annotations
 */
package com.snowedunderproductions.graphprobe.database;
