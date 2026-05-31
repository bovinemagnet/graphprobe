package com.snowedunderproductions.graphprobe.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for JUnit 5 {@link ArgumentsProvider} implementations that
 * source their test arguments from a PostgreSQL database.
 *
 * <p>Subclasses only need to implement three template methods:
 * <ul>
 *   <li>{@link #getSQL()} — the SQL query whose result set defines the test cases.</li>
 *   <li>{@link #extractArguments(java.sql.ResultSet)} — converts one result-set row into a
 *       JUnit {@link org.junit.jupiter.params.provider.Arguments} instance.</li>
 *   <li>{@link #getCacheKey()} — a stable, unique string key used by the Caffeine cache
 *       so that repeated parameterised-test runs within the same JVM do not re-query
 *       the database.</li>
 * </ul>
 *
 * <p>The shared {@link DatabaseConnectionManager} singleton is initialised automatically
 * the first time any subclass is loaded.  All providers share the same HikariCP pool and
 * the same Caffeine result cache (5-minute TTL, up to 10 000 entries).
 *
 * <p>Providers are referenced from the
 * {@link com.snowedunderproductions.graphprobe.annotations.DynamicSource} annotation and
 * dispatched by
 * {@link com.snowedunderproductions.graphprobe.annotations.DynamicSourceProvider}, which
 * also supports a CSV fallback when the {@code USE_CSV} environment variable is set to
 * {@code true}.
 *
 * <h3>Minimal subclass example</h3>
 * <pre>{@code
 * public class BookingArgumentsProvider extends BaseArgumentsProvider {
 *
 *     @Override
 *     protected String getSQL() {
 *         return "SELECT booking_id, status FROM bookings WHERE active = true LIMIT 500";
 *     }
 *
 *     @Override
 *     protected Arguments extractArguments(ResultSet rs) throws SQLException {
 *         return Arguments.of(
 *             getSafeString(rs, "booking_id"),
 *             getSafeString(rs, "status")
 *         );
 *     }
 *
 *     @Override
 *     protected String getCacheKey() {
 *         return "bookings-active";
 *     }
 * }
 * }</pre>
 *
 * @see com.snowedunderproductions.graphprobe.annotations.DynamicSource
 * @see com.snowedunderproductions.graphprobe.annotations.DynamicSourceProvider
 * @see DatabaseConnectionManager
 */
public abstract class BaseArgumentsProvider implements ArgumentsProvider {

    private static final Logger log = LoggerFactory.getLogger(
        BaseArgumentsProvider.class
    );

    // Shared cache configuration
    private static final Cache<String, List<Arguments>> SHARED_CACHE =
        Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES) // Increased from 2 to 5 minutes
            .maximumSize(10000) // Reasonable cache size
            .recordStats()
            .build();

    // Connection manager (singleton)
    private static final DatabaseConnectionManager connectionManager =
        DatabaseConnectionManager.getInstance();

    // Static initialization block to ensure connection pool is ready
    static {
        initializeConnectionPool();
    }

    /**
     * Initialize the connection pool if not already done.
     * This ensures the pool is ready before any provider needs it.
     */
    private static void initializeConnectionPool() {
        try {
            if (!connectionManager.isInitialized()) {
                log.info(
                    "Initializing database connection pool for ArgumentsProviders"
                );
                connectionManager.initialize();

                // Log initial health status
                var healthStatus = connectionManager.getHealthStatus();
                if (healthStatus.isHealthy()) {
                    log.info(
                        "Database connection pool initialized successfully: {}",
                        healthStatus.getStatistics()
                    );
                } else {
                    log.error(
                        "Database connection pool initialization failed: {}",
                        healthStatus.getMessage()
                    );
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize database connection pool", e);
            // Don't fail here - let individual providers handle the error
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public final Stream<? extends Arguments> provideArguments(
        ExtensionContext context
    ) throws Exception {
        String cacheKey = getCacheKey();

        try {
            // Try to get results from cache first
            List<Arguments> cachedResults = SHARED_CACHE.get(cacheKey, key -> {
                try {
                    return fetchResultsFromDatabase();
                } catch (Exception e) {
                    log.error(
                        "Failed to fetch results from database for cache key: {}",
                        key,
                        e
                    );
                    throw new RuntimeException("Database query failed", e);
                }
            });

            if (cachedResults.isEmpty()) {
                log.warn(
                    "No arguments found for provider: {} (cache key: {})",
                    getClass().getSimpleName(),
                    cacheKey
                );
            } else {
                log.debug(
                    "Returning {} arguments for provider: {} (cache key: {})",
                    cachedResults.size(),
                    getClass().getSimpleName(),
                    cacheKey
                );
            }

            return cachedResults.stream();
        } catch (Exception e) {
            log.error(
                "Failed to provide arguments for provider: {}",
                getClass().getSimpleName(),
                e
            );

            // Log connection pool statistics for debugging
            if (connectionManager.isInitialized()) {
                log.debug(
                    "Connection pool statistics: {}",
                    connectionManager.getStatistics()
                );
            }

            throw new RuntimeException("Failed to provide test arguments", e);
        }
    }

    /**
     * Fetches results from the database using the connection pool.
     * This method handles connection management and proper resource cleanup.
     *
     * @return list of Arguments extracted from the database
     * @throws SQLException if database access fails
     */
    private List<Arguments> fetchResultsFromDatabase() throws SQLException {
        if (!connectionManager.isInitialized()) {
            throw new SQLException(
                "Database connection manager is not initialized"
            );
        }

        String sql = getSQL();
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalStateException(
                "SQL query cannot be null or empty"
            );
        }

        log.debug(
            "Executing SQL query for {}: {}",
            getClass().getSimpleName(),
            sql.length() > 100 ? sql.substring(0, 100) + "..." : sql
        );

        return connectionManager.executeWithConnection(connection -> {
            List<Arguments> argumentsList = new ArrayList<>();

            try (
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)
            ) {
                int rowCount = 0;
                while (resultSet.next()) {
                    try {
                        Arguments args = extractArguments(resultSet);
                        if (args != null) {
                            argumentsList.add(args);
                            rowCount++;
                        }
                    } catch (Exception e) {
                        log.warn(
                            "Failed to extract arguments from result set row {}: {}",
                            rowCount + 1,
                            e.getMessage()
                        );
                    }
                }

                log.debug(
                    "Extracted {} arguments from {} rows for provider: {}",
                    argumentsList.size(),
                    rowCount,
                    getClass().getSimpleName()
                );

                return argumentsList;
            }
        });
    }

    /**
     * Returns the SQL query whose result set defines the test arguments.
     *
     * <p>The query is executed once per cache miss and the results are cached under
     * the key returned by {@link #getCacheKey()}.  The query should include an
     * appropriate {@code LIMIT} clause to cap the number of test cases generated.
     *
     * @return the SQL query string; must not be {@code null} or blank
     */
    protected abstract String getSQL();

    /**
     * Converts the current row of the given {@link java.sql.ResultSet} into a JUnit
     * {@link org.junit.jupiter.params.provider.Arguments} instance.
     *
     * <p>Use the helper methods {@link #getSafeString(java.sql.ResultSet, String)},
     * {@link #getSafeLongAsString(java.sql.ResultSet, String)}, and
     * {@link #getSafeIntAsString(java.sql.ResultSet, String)} to handle {@code NULL}
     * values consistently.
     *
     * @param resultSet a {@link java.sql.ResultSet} positioned at a valid row; must not be
     *                  advanced or closed by this method
     * @return an {@link org.junit.jupiter.params.provider.Arguments} instance for this row,
     *         or {@code null} to skip the row
     * @throws SQLException if column access fails
     */
    protected abstract Arguments extractArguments(ResultSet resultSet)
        throws SQLException;

    /**
     * Returns a stable, unique key used to store and retrieve this provider's results
     * in the shared Caffeine cache.
     *
     * <p>The key must be unique across all {@code BaseArgumentsProvider} subclasses to
     * prevent cache collisions, and must not change between JVM restarts within a single
     * test run (i.e., do not include timestamps or random values).
     *
     * @return a non-null, non-blank cache key unique to this provider
     */
    protected abstract String getCacheKey();

    /**
     * Returns the string value of the named column, substituting an empty string for
     * SQL {@code NULL} to prevent {@link NullPointerException} in test assertions.
     *
     * @param resultSet  the current result set; must be positioned at a valid row
     * @param columnName the column label as defined in the SQL query
     * @return the column value, or {@code ""} if the value is {@code NULL}
     * @throws SQLException if the column does not exist or cannot be read
     */
    protected String getSafeString(ResultSet resultSet, String columnName)
        throws SQLException {
        String value = resultSet.getString(columnName);
        return value != null ? value : "";
    }

    /**
     * Returns the {@code long} value of the named column as a {@link String},
     * substituting {@code "0"} for SQL {@code NULL}.
     *
     * <p>Useful for numeric identifier columns that GraphQL parameters expect as strings.
     *
     * @param resultSet  the current result set; must be positioned at a valid row
     * @param columnName the column label as defined in the SQL query
     * @return the column value formatted as a decimal string, or {@code "0"} if {@code NULL}
     * @throws SQLException if the column does not exist or cannot be read
     */
    protected String getSafeLongAsString(
        ResultSet resultSet,
        String columnName
    ) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? "0" : String.valueOf(value);
    }

    /**
     * Returns the {@code int} value of the named column as a {@link String},
     * substituting {@code "0"} for SQL {@code NULL}.
     *
     * @param resultSet  the current result set; must be positioned at a valid row
     * @param columnName the column label as defined in the SQL query
     * @return the column value formatted as a decimal string, or {@code "0"} if {@code NULL}
     * @throws SQLException if the column does not exist or cannot be read
     */
    protected String getSafeIntAsString(ResultSet resultSet, String columnName)
        throws SQLException {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? "0" : String.valueOf(value);
    }

    /**
     * Returns formatted Caffeine cache statistics for monitoring and debugging.
     *
     * <p>The returned string includes hit count, miss count, eviction count, and estimated
     * current cache size.
     *
     * @return a human-readable statistics string; never {@code null}
     */
    public static String getCacheStatistics() {
        var stats = SHARED_CACHE.stats();
        return String.format(
            "Cache Stats: hits=%d, misses=%d, evictions=%d, size=%d",
            stats.hitCount(),
            stats.missCount(),
            stats.evictionCount(),
            SHARED_CACHE.estimatedSize()
        );
    }

    /**
     * Invalidates all entries in the shared Caffeine result cache.
     *
     * <p>Subsequent calls to {@link #provideArguments} will re-query the database.
     * Useful in test-framework self-tests or when the underlying data is known to
     * have changed mid-run.
     */
    public static void clearCache() {
        SHARED_CACHE.invalidateAll();
        log.info("ArgumentsProvider cache cleared");
    }

    /**
     * Returns the shared {@link DatabaseConnectionManager} singleton.
     *
     * <p>Intended for subclasses that need to perform connection-level operations
     * beyond the scope of {@link #extractArguments(java.sql.ResultSet)}.
     *
     * @return the singleton {@link DatabaseConnectionManager}; never {@code null}
     */
    protected static DatabaseConnectionManager getConnectionManager() {
        return connectionManager;
    }

    /**
     * Validates that the database is accessible by checking the
     * {@link DatabaseConnectionManager} initialisation state and health.
     *
     * <p>Subclasses may call this method at the start of their {@link #getSQL()}
     * implementation to fail early with a descriptive message when the database is
     * unavailable.
     *
     * @throws SQLException if the connection manager is uninitialised or the pool
     *                      reports an unhealthy state
     */
    protected void validateDatabaseConnection() throws SQLException {
        if (!connectionManager.isInitialized()) {
            throw new SQLException(
                "Database connection manager is not initialized"
            );
        }

        var healthStatus = connectionManager.getHealthStatus();
        if (!healthStatus.isHealthy()) {
            throw new SQLException(
                "Database connection is not healthy: " +
                healthStatus.getMessage()
            );
        }
    }
}
