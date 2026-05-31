package com.snowedunderproductions.graphprobe.database;

import com.snowedunderproductions.graphprobe.config.EnvConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static factory for creating and managing {@link com.zaxxer.hikari.HikariDataSource}
 * instances optimised for GraphQL integration-test workloads.
 *
 * <p>All configurable values are read from environment variables via
 * {@link com.snowedunderproductions.graphprobe.config.EnvConfig}, falling back to
 * sensible defaults where shown below.  No values are hardcoded so the same
 * framework binary can connect to different databases across CI environments.
 *
 * <h3>Environment variables</h3>
 * <ul>
 *   <li>{@code POSTGRES_URL} <em>(required)</em> — JDBC connection URL.</li>
 *   <li>{@code POSTGRES_USER} <em>(required)</em> — database username.</li>
 *   <li>{@code POSTGRES_PASSWORD} <em>(required)</em> — database password.</li>
 *   <li>{@code HIKARI_POOL_NAME} — pool name used in logs and JMX (default: {@code GraphQLTestPool}).</li>
 *   <li>{@code HIKARI_MINIMUM_IDLE} — minimum idle connections (default: 3).</li>
 *   <li>{@code HIKARI_MAXIMUM_POOL_SIZE} — maximum pool size (default: 15).</li>
 *   <li>{@code HIKARI_CONNECTION_TIMEOUT_MS} — connection-acquisition timeout in ms (default: 10 000).</li>
 *   <li>{@code HIKARI_IDLE_TIMEOUT_MS} — idle-connection eviction timeout in ms (default: 600 000).</li>
 *   <li>{@code HIKARI_MAX_LIFETIME_MS} — maximum connection lifetime in ms (default: 1 800 000).</li>
 *   <li>{@code HIKARI_LEAK_DETECTION_THRESHOLD_MS} — log a warning when a connection is held longer
 *       than this many ms (default: 20 000).</li>
 *   <li>{@code HIKARI_VALIDATION_TIMEOUT_MS} — connection-validation timeout in ms (default: 3 000).</li>
 *   <li>{@code POSTGRES_SSL_MODE} — PostgreSQL SSL mode passed to the driver (default: {@code prefer}).</li>
 * </ul>
 *
 * <p>This class is not instantiated directly; all methods are static.  The singleton
 * pool is owned by {@link DatabaseConnectionManager}.
 *
 * @see DatabaseConnectionManager
 * @see com.snowedunderproductions.graphprobe.config.EnvConfig
 */
public class HikariConfig {

    private static final Logger log = LoggerFactory.getLogger(HikariConfig.class);

    // Default configuration constants
    private static final int DEFAULT_MINIMUM_IDLE = 3;
    private static final int DEFAULT_MAXIMUM_POOL_SIZE = 15;
    private static final long DEFAULT_CONNECTION_TIMEOUT_MS = 10_000; // 10 seconds (fail fast under parallel load)
    private static final long DEFAULT_IDLE_TIMEOUT_MS = 600_000; // 10 minutes
    private static final long DEFAULT_MAX_LIFETIME_MS = 1_800_000; // 30 minutes
    private static final long DEFAULT_LEAK_DETECTION_THRESHOLD_MS = 20_000; // 20 seconds (surface leaks sooner)
    private static final long DEFAULT_VALIDATION_TIMEOUT_MS = 3_000; // 3 seconds

    /**
     * Creates and configures a {@link HikariDataSource} with settings optimised for
     * integration-test workloads.
     *
     * <p>Configuration is resolved in this priority order:
     * <ol>
     *   <li>Environment variables (highest priority — suitable for CI/CD pipelines).</li>
     *   <li>Hard-coded defaults (lowest priority — safe values for local development).</li>
     * </ol>
     *
     * <p>PostgreSQL-specific driver properties (prepared-statement cache, batch rewrites,
     * result-set metadata cache, etc.) are applied automatically.
     *
     * @return a fully configured and validated {@link HikariDataSource}
     * @throws IllegalStateException if a required environment variable
     *         ({@code POSTGRES_URL}, {@code POSTGRES_USER}, or {@code POSTGRES_PASSWORD})
     *         is absent
     */
    public static HikariDataSource createDataSource() {
        // Validate required environment variables
        String jdbcUrl = EnvConfig.getRequired("POSTGRES_URL");
        String username = EnvConfig.getRequired("POSTGRES_USER");
        String password = EnvConfig.getRequired("POSTGRES_PASSWORD");

        com.zaxxer.hikari.HikariConfig config =
            new com.zaxxer.hikari.HikariConfig();

        // === Basic Database Configuration ===
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");

        // === Pool Sizing Configuration ===
        // Optimized for integration testing: moderate concurrency with efficient resource usage
        config.setMinimumIdle(
            EnvConfig.getInt("HIKARI_MINIMUM_IDLE", DEFAULT_MINIMUM_IDLE)
        );
        config.setMaximumPoolSize(
            EnvConfig.getInt(
                "HIKARI_MAXIMUM_POOL_SIZE",
                DEFAULT_MAXIMUM_POOL_SIZE
            )
        );

        // === Connection Timeout Configuration ===
        config.setConnectionTimeout(
            EnvConfig.getInt(
                "HIKARI_CONNECTION_TIMEOUT_MS",
                (int) DEFAULT_CONNECTION_TIMEOUT_MS
            )
        );
        config.setIdleTimeout(
            EnvConfig.getInt(
                "HIKARI_IDLE_TIMEOUT_MS",
                (int) DEFAULT_IDLE_TIMEOUT_MS
            )
        );
        config.setMaxLifetime(
            EnvConfig.getInt(
                "HIKARI_MAX_LIFETIME_MS",
                (int) DEFAULT_MAX_LIFETIME_MS
            )
        );
        config.setValidationTimeout(
            EnvConfig.getInt(
                "HIKARI_VALIDATION_TIMEOUT_MS",
                (int) DEFAULT_VALIDATION_TIMEOUT_MS
            )
        );

        // === Connection Validation ===
        config.setConnectionTestQuery("SELECT 1");
        config.setLeakDetectionThreshold(
            EnvConfig.getInt(
                "HIKARI_LEAK_DETECTION_THRESHOLD_MS",
                (int) DEFAULT_LEAK_DETECTION_THRESHOLD_MS
            )
        );

        // === Pool Identification and Monitoring ===
        config.setPoolName(EnvConfig.get("HIKARI_POOL_NAME", "GraphQLTestPool"));
        config.setRegisterMbeans(true); // Enable JMX monitoring

        // === PostgreSQL-specific optimizations ===
        // These properties optimize the connection for PostgreSQL and testing workloads
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        // === SSL Configuration ===
        // Configure SSL based on environment variable
        String sslMode = EnvConfig.get("POSTGRES_SSL_MODE", "prefer");
        config.addDataSourceProperty("sslmode", sslMode);

        // === Testing-specific Configuration ===
        // Allow aggressive connection closing for faster test cleanup
        config.setAllowPoolSuspension(true);

        // Create and validate the data source
        HikariDataSource dataSource = new HikariDataSource(config);

        log.info("HikariCP DataSource created successfully:");
        log.info("  JDBC URL: {}", maskJdbcUrl(jdbcUrl));
        log.info("  Username: {}", username);
        log.info("  Pool Name: {}", config.getPoolName());
        log.info("  Minimum Idle: {}", config.getMinimumIdle());
        log.info("  Maximum Pool Size: {}", config.getMaximumPoolSize());
        log.info("  Connection Timeout: {}ms", config.getConnectionTimeout());
        log.info("  Idle Timeout: {}ms", config.getIdleTimeout());
        log.info("  Max Lifetime: {}ms", config.getMaxLifetime());
        log.info(
            "  Leak Detection Threshold: {}ms",
            config.getLeakDetectionThreshold()
        );

        return dataSource;
    }

    /**
     * Creates a {@link HikariDataSource} with a reduced pool size and a custom
     * connection timeout, for testing scenarios that require tighter resource limits.
     *
     * <p>Calls {@link #createDataSource()} internally and then overrides
     * {@code maximumPoolSize}, {@code connectionTimeout}, and {@code minimumIdle}
     * on the resulting source.
     *
     * @param maxPoolSize        the maximum number of connections in the pool
     * @param connectionTimeoutMs connection-acquisition timeout in milliseconds
     * @return a configured {@link HikariDataSource} with the specified overrides applied
     * @throws IllegalStateException if a required environment variable is absent
     * @see #createDataSource()
     */
    public static HikariDataSource createMinimalDataSource(
        int maxPoolSize,
        long connectionTimeoutMs
    ) {
        HikariDataSource dataSource = createDataSource();

        // Override specific settings
        dataSource.setMaximumPoolSize(maxPoolSize);
        dataSource.setConnectionTimeout(connectionTimeoutMs);
        dataSource.setMinimumIdle(Math.min(1, maxPoolSize / 2));

        log.info(
            "Created minimal DataSource with maxPoolSize={}, connectionTimeout={}ms",
            maxPoolSize,
            connectionTimeoutMs
        );

        return dataSource;
    }

    /**
     * Validates that a connection can be acquired from the given data source by executing
     * a lightweight probe query ({@code SELECT 1}).
     *
     * <p>Call this method immediately after {@link #createDataSource()} to surface
     * connectivity problems before any test begins.
     *
     * @param dataSource the data source to validate; must not be {@code null} or closed
     * @throws IllegalArgumentException if {@code dataSource} is {@code null}
     * @throws IllegalStateException    if {@code dataSource} is already closed
     * @throws RuntimeException         if a connection cannot be acquired or the probe query fails
     */
    public static void validateConfiguration(HikariDataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource cannot be null");
        }

        if (dataSource.isClosed()) {
            throw new IllegalStateException("DataSource is closed");
        }

        try (var connection = dataSource.getConnection()) {
            var statement = connection.createStatement();
            var resultSet = statement.executeQuery(
                "SELECT 1 as test_connection"
            );

            if (resultSet.next() && resultSet.getInt("test_connection") == 1) {
                log.info("Database connection validation successful");
            } else {
                throw new RuntimeException(
                    "Database connection validation failed: unexpected result"
                );
            }
        } catch (Exception e) {
            log.error("Database connection validation failed", e);
            throw new RuntimeException(
                "Failed to validate database connection",
                e
            );
        }
    }

    /**
     * Gracefully shuts down the given data source, waiting up to {@code timeoutSeconds}
     * for any active connections to be returned before closing the pool.
     *
     * <p>The pool is first suspended (preventing new connection requests), then the method
     * polls until active connections reach zero or the timeout elapses.  If a graceful
     * close fails, the pool is force-closed.  If {@code dataSource} is {@code null} or
     * already closed this method returns immediately.
     *
     * @param dataSource     the data source to shut down
     * @param timeoutSeconds maximum number of seconds to wait for active connections to finish
     */
    public static void gracefulShutdown(
        HikariDataSource dataSource,
        int timeoutSeconds
    ) {
        if (dataSource == null || dataSource.isClosed()) {
            return;
        }

        try {
            log.info(
                "Initiating graceful shutdown of HikariCP pool: {}",
                dataSource.getPoolName()
            );

            // Suspend the pool to prevent new connections
            dataSource.getHikariPoolMXBean().suspendPool();

            // Wait for active connections to complete
            long startTime = System.currentTimeMillis();
            while (
                dataSource.getHikariPoolMXBean().getActiveConnections() > 0 &&
                (System.currentTimeMillis() - startTime) <
                TimeUnit.SECONDS.toMillis(timeoutSeconds)
            ) {
                log.debug(
                    "Waiting for {} active connections to complete...",
                    dataSource.getHikariPoolMXBean().getActiveConnections()
                );

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Close the data source
            dataSource.close();

            log.info(
                "HikariCP pool shutdown completed: {}",
                dataSource.getPoolName()
            );
        } catch (Exception e) {
            log.error("Error during graceful shutdown of HikariCP pool", e);
            // Force close if graceful shutdown fails
            try {
                dataSource.close();
            } catch (Exception closeException) {
                log.error(
                    "Error during force close of HikariCP pool",
                    closeException
                );
            }
        }
    }

    /**
     * Returns a copy of {@code jdbcUrl} with any inline {@code password=…} parameter
     * replaced by {@code password=***}, making the URL safe for log output.
     *
     * @param jdbcUrl the original JDBC URL; may be {@code null}
     * @return the masked URL, or {@code null} if {@code jdbcUrl} was {@code null}
     */
    private static String maskJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }

        // Replace password parameter if present
        return jdbcUrl.replaceAll("password=[^&\\s]+", "password=***");
    }

    /**
     * Returns a formatted string containing the current pool statistics for the given
     * data source, suitable for log output and debugging.
     *
     * <p>The format is:
     * <pre>{@code
     * Pool Statistics [<poolName>]: Active=N, Idle=N, Total=N, Pending=N
     * }</pre>
     *
     * @param dataSource the data source to query; if {@code null} or closed, a descriptive
     *                   message is returned instead
     * @return a human-readable statistics string; never {@code null}
     */
    public static String getPoolStatistics(HikariDataSource dataSource) {
        if (dataSource == null || dataSource.isClosed()) {
            return "DataSource is null or closed";
        }

        var poolMXBean = dataSource.getHikariPoolMXBean();

        return String.format(
            "Pool Statistics [%s]: Active=%d, Idle=%d, Total=%d, Pending=%d",
            dataSource.getPoolName(),
            poolMXBean.getActiveConnections(),
            poolMXBean.getIdleConnections(),
            poolMXBean.getTotalConnections(),
            poolMXBean.getThreadsAwaitingConnection()
        );
    }
}
