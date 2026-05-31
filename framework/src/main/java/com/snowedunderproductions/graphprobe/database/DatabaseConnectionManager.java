package com.snowedunderproductions.graphprobe.database;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Process-wide singleton that manages the HikariCP connection pool for all
 * database-driven test components in GraphProbe.
 *
 * <p>A single pool instance is shared across every
 * {@link BaseArgumentsProvider} subclass and every JUnit extension
 * ({@link DatabaseTestLifecycle}) that runs in the same JVM, avoiding the overhead
 * of creating multiple pools.  The pool itself is created and configured by
 * {@link HikariConfig}.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Obtain the singleton: {@code DatabaseConnectionManager.getInstance()}</li>
 *   <li>Initialise the pool: {@code manager.initialize()} (idempotent — safe to call more than once).</li>
 *   <li>Use connections via {@link #executeWithConnection(DatabaseOperation)} or
 *       {@link #getConnection()} with try-with-resources.</li>
 *   <li>Shut down at the end of the test run: {@code manager.shutdown(timeoutSeconds)}.</li>
 * </ol>
 *
 * <p>A JVM shutdown hook is registered automatically on the first call to
 * {@link #initialize()} so the pool is always closed cleanly even if shutdown is not
 * called explicitly.
 *
 * <h3>Thread safety</h3>
 * <p>All public methods are thread-safe.  Initialisation uses double-checked locking
 * with a {@code volatile} instance field.  Connection access is guarded by a
 * {@link java.util.concurrent.locks.ReentrantReadWriteLock} so multiple threads can
 * acquire connections concurrently while shutdown holds the write lock.
 *
 * @see HikariConfig
 * @see BaseArgumentsProvider
 * @see DatabaseTestLifecycle
 */
public class DatabaseConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(
        DatabaseConnectionManager.class
    );

    // Singleton instance
    private static volatile DatabaseConnectionManager instance;
    private static final Object initLock = new Object();

    // Connection pool management
    private volatile HikariDataSource dataSource;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    // Statistics tracking
    private volatile long connectionsRequested = 0;
    private volatile long connectionsFailed = 0;
    private volatile long connectionsReturned = 0;

    /**
     * Private constructor to enforce singleton pattern.
     */
    private DatabaseConnectionManager() {
        // Private constructor
    }

    /**
     * Returns the singleton instance of {@code DatabaseConnectionManager},
     * creating it on the first call.
     *
     * <p>Uses double-checked locking with a {@code volatile} field to ensure safe
     * lazy initialisation under concurrent access.  The returned instance is not yet
     * connected to a database; call {@link #initialize()} before requesting connections.
     *
     * @return the singleton {@code DatabaseConnectionManager}; never {@code null}
     */
    public static DatabaseConnectionManager getInstance() {
        if (instance == null) {
            synchronized (initLock) {
                if (instance == null) {
                    instance = new DatabaseConnectionManager();
                }
            }
        }
        return instance;
    }

    /**
     * Initialises the connection pool using default configuration derived from environment
     * variables (see {@link HikariConfig} for the full variable list).
     *
     * <p>This method is idempotent: subsequent calls while the pool is already
     * initialised are silently ignored.  A JVM shutdown hook is registered on the
     * first successful call.
     *
     * @throws RuntimeException if the pool cannot be created or the initial
     *                          connectivity validation fails
     * @see #initialize(int, long)
     * @see HikariConfig#createDataSource()
     */
    public void initialize() {
        if (initialized.get()) {
            log.debug("DatabaseConnectionManager already initialized");
            return;
        }

        rwLock.writeLock().lock();
        try {
            if (initialized.get()) {
                return; // Double-check after acquiring lock
            }

            log.info("Initializing DatabaseConnectionManager with HikariCP");

            // Create the data source
            dataSource = HikariConfig.createDataSource();

            // Validate the configuration
            HikariConfig.validateConfiguration(dataSource);

            // Register shutdown hook for cleanup
            registerShutdownHook();

            initialized.set(true);
            log.info(
                "DatabaseConnectionManager initialization completed successfully"
            );
        } catch (Exception e) {
            log.error("Failed to initialize DatabaseConnectionManager", e);
            cleanup();
            throw new RuntimeException(
                "Database connection manager initialization failed",
                e
            );
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Initialises the connection pool with an explicit maximum pool size and connection
     * timeout, overriding the values that would otherwise come from environment variables.
     *
     * <p>Use this overload in test scenarios that require tighter resource limits than
     * the defaults.  Like {@link #initialize()}, this method is idempotent.
     *
     * @param maxPoolSize         maximum number of connections in the pool
     * @param connectionTimeoutMs connection-acquisition timeout in milliseconds
     * @throws RuntimeException if the pool cannot be created or connectivity validation fails
     * @see HikariConfig#createMinimalDataSource(int, long)
     */
    public void initialize(int maxPoolSize, long connectionTimeoutMs) {
        if (initialized.get()) {
            log.debug("DatabaseConnectionManager already initialized");
            return;
        }

        rwLock.writeLock().lock();
        try {
            if (initialized.get()) {
                return; // Double-check after acquiring lock
            }

            log.info(
                "Initializing DatabaseConnectionManager with custom configuration: maxPoolSize={}, connectionTimeout={}ms",
                maxPoolSize,
                connectionTimeoutMs
            );

            // Create the data source with custom settings
            dataSource = HikariConfig.createMinimalDataSource(
                maxPoolSize,
                connectionTimeoutMs
            );

            // Validate the configuration
            HikariConfig.validateConfiguration(dataSource);

            // Register shutdown hook for cleanup
            registerShutdownHook();

            initialized.set(true);
            log.info(
                "DatabaseConnectionManager initialization completed successfully"
            );
        } catch (Exception e) {
            log.error("Failed to initialize DatabaseConnectionManager", e);
            cleanup();
            throw new RuntimeException(
                "Database connection manager initialization failed",
                e
            );
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Gets a database connection from the pool.
     *
     * The caller is responsible for closing the connection when done.
     * Use try-with-resources for automatic connection management:
     *
     * <pre>
     * try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
     *     // Use the connection
     * }
     * </pre>
     *
     * @return a database connection
     * @throws SQLException if unable to get a connection
     * @throws IllegalStateException if the manager is not initialized or is shut down
     */
    public Connection getConnection() throws SQLException {
        if (!initialized.get()) {
            throw new IllegalStateException(
                "DatabaseConnectionManager is not initialized. Call initialize() first."
            );
        }

        if (shutdownInProgress.get()) {
            throw new IllegalStateException(
                "DatabaseConnectionManager is shutting down. No new connections allowed."
            );
        }

        rwLock.readLock().lock();
        try {
            if (dataSource == null || dataSource.isClosed()) {
                connectionsFailed++;
                throw new SQLException(
                    "DataSource is not available or has been closed"
                );
            }

            connectionsRequested++;
            Connection connection = dataSource.getConnection();

            // Validate the connection before returning it
            if (connection == null || connection.isClosed()) {
                connectionsFailed++;
                throw new SQLException("Received invalid connection from pool");
            }

            log.debug("Connection acquired from pool. {}", getStatistics());
            return connection;
        } catch (SQLException e) {
            connectionsFailed++;
            log.error("Failed to get connection from pool", e);
            throw e;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Executes a value-returning database operation using a connection borrowed from the
     * pool, returning the connection automatically when the operation completes.
     *
     * <p>The supplied lambda receives an open, valid {@link java.sql.Connection}; the
     * manager takes care of acquisition and release.  Prefer this method over
     * {@link #getConnection()} to avoid forgetting to close the connection.
     *
     * @param <T>       the return type of the operation
     * @param operation the database operation to execute; must not be {@code null}
     * @return the value returned by {@code operation}
     * @throws SQLException          if the operation or connection acquisition fails
     * @throws IllegalStateException if the manager has not been initialised or is shutting down
     * @see DatabaseOperation
     */
    public <T> T executeWithConnection(DatabaseOperation<T> operation)
        throws SQLException {
        try (Connection connection = getConnection()) {
            T result = operation.execute(connection);
            connectionsReturned++;
            return result;
        }
    }

    /**
     * Executes a void database operation using a connection borrowed from the pool,
     * returning the connection automatically when the operation completes.
     *
     * @param operation the database operation to execute; must not be {@code null}
     * @throws SQLException          if the operation or connection acquisition fails
     * @throws IllegalStateException if the manager has not been initialised or is shutting down
     * @see VoidDatabaseOperation
     */
    public void executeWithConnection(VoidDatabaseOperation operation)
        throws SQLException {
        try (Connection connection = getConnection()) {
            operation.execute(connection);
            connectionsReturned++;
        }
    }

    /**
     * Checks if the connection manager is initialized and ready to use.
     *
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return (
            initialized.get() && dataSource != null && !dataSource.isClosed()
        );
    }

    /**
     * Checks if the connection manager is currently shutting down.
     *
     * @return true if shutdown is in progress, false otherwise
     */
    public boolean isShuttingDown() {
        return shutdownInProgress.get();
    }

    /**
     * Gets current connection pool statistics for monitoring and debugging.
     *
     * @return formatted statistics string
     */
    public String getStatistics() {
        if (!initialized.get() || dataSource == null) {
            return "DatabaseConnectionManager: Not initialized";
        }

        String poolStats = HikariConfig.getPoolStatistics(dataSource);
        return String.format(
            "%s, Requested=%d, Failed=%d, Returned=%d",
            poolStats,
            connectionsRequested,
            connectionsFailed,
            connectionsReturned
        );
    }

    /**
     * Returns a {@link HealthStatus} snapshot describing the current health of the
     * connection pool.
     *
     * <p>A healthy status is reported only when the pool is initialised, not shutting
     * down, and able to vend a test connection.  Any other condition produces a
     * non-healthy status with a descriptive message.
     *
     * @return a {@link HealthStatus} instance; never {@code null}
     */
    public HealthStatus getHealthStatus() {
        if (!initialized.get()) {
            return new HealthStatus(false, "Not initialized", null);
        }

        if (shutdownInProgress.get()) {
            return new HealthStatus(false, "Shutdown in progress", null);
        }

        if (dataSource == null || dataSource.isClosed()) {
            return new HealthStatus(false, "DataSource closed", null);
        }

        try {
            // Test connection acquisition
            try (Connection conn = dataSource.getConnection()) {
                return new HealthStatus(true, "Healthy", getStatistics());
            }
        } catch (Exception e) {
            return new HealthStatus(
                false,
                "Connection test failed: " + e.getMessage(),
                getStatistics()
            );
        }
    }

    /**
     * Gracefully shuts down the connection manager, waiting up to {@code timeoutSeconds}
     * for active connections to be returned before closing the pool.
     *
     * <p>If shutdown is already in progress this method returns immediately.  If the
     * manager was never initialised it also returns immediately.
     *
     * @param timeoutSeconds maximum number of seconds to wait for the pool to drain
     * @see HikariConfig#gracefulShutdown(com.zaxxer.hikari.HikariDataSource, int)
     */
    public void shutdown(int timeoutSeconds) {
        if (!initialized.get()) {
            log.debug(
                "DatabaseConnectionManager not initialized, no shutdown needed"
            );
            return;
        }

        if (!shutdownInProgress.compareAndSet(false, true)) {
            log.debug("Shutdown already in progress");
            return;
        }

        rwLock.writeLock().lock();
        try {
            log.info("Shutting down DatabaseConnectionManager...");

            if (dataSource != null) {
                HikariConfig.gracefulShutdown(dataSource, timeoutSeconds);
            }

            cleanup();
            log.info("DatabaseConnectionManager shutdown completed");
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Immediately closes the connection pool without waiting for active connections to
     * finish.
     *
     * <p>This method should only be used when a clean {@link #shutdown(int)} is not
     * possible (for example, during an unrecoverable error or emergency test teardown).
     * Active connections may be interrupted.
     */
    public void forceShutdown() {
        if (!initialized.get()) {
            return;
        }

        shutdownInProgress.set(true);
        rwLock.writeLock().lock();
        try {
            log.warn("Force shutdown of DatabaseConnectionManager initiated");

            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }

            cleanup();
            log.info("DatabaseConnectionManager force shutdown completed");
        } catch (Exception e) {
            log.error("Error during force shutdown", e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Registers a JVM shutdown hook to ensure clean shutdown.
     */
    private void registerShutdownHook() {
        Runtime.getRuntime()
            .addShutdownHook(
                new Thread(
                    () -> {
                        if (initialized.get() && !shutdownInProgress.get()) {
                            log.info(
                                "JVM shutdown detected, cleaning up DatabaseConnectionManager"
                            );
                            shutdown(5); // 5 second timeout for shutdown hook
                        }
                    },
                    "DatabaseConnectionManager-ShutdownHook"
                )
            );
    }

    /**
     * Internal cleanup method.
     */
    private void cleanup() {
        dataSource = null;
        initialized.set(false);
    }

    /**
     * Functional interface for database operations that return a value.
     *
     * <p>Implementations receive an open {@link Connection} obtained from the pool and
     * must not close it; the manager handles connection lifecycle.
     *
     * <p>Usage example:
     * <pre>{@code
     * List<String> ids = manager.executeWithConnection(conn -> {
     *     List<String> result = new ArrayList<>();
     *     try (Statement st = conn.createStatement();
     *          ResultSet rs = st.executeQuery("SELECT id FROM items")) {
     *         while (rs.next()) result.add(rs.getString(1));
     *     }
     *     return result;
     * });
     * }</pre>
     *
     * @param <T> the type returned by the operation
     * @see DatabaseConnectionManager#executeWithConnection(DatabaseOperation)
     */
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        /**
         * Executes a database operation using the supplied connection.
         *
         * @param connection an open connection borrowed from the pool; must not be closed
         * @return the result of the operation
         * @throws SQLException if the operation fails
         */
        T execute(Connection connection) throws SQLException;
    }

    /**
     * Functional interface for database operations that do not return a value.
     *
     * @see DatabaseConnectionManager#executeWithConnection(VoidDatabaseOperation)
     */
    @FunctionalInterface
    public interface VoidDatabaseOperation {
        /**
         * Executes a database operation using the supplied connection.
         *
         * @param connection an open connection borrowed from the pool; must not be closed
         * @throws SQLException if the operation fails
         */
        void execute(Connection connection) throws SQLException;
    }

    /**
     * Immutable snapshot of the connection-pool health at a point in time.
     *
     * <p>Instances are returned by {@link DatabaseConnectionManager#getHealthStatus()} and
     * are also used by {@link DatabaseTestLifecycle} to validate connectivity before
     * tests run.
     *
     * @see DatabaseConnectionManager#getHealthStatus()
     */
    public static class HealthStatus {

        private final boolean healthy;
        private final String message;
        private final String statistics;

        public HealthStatus(
            boolean healthy,
            String message,
            String statistics
        ) {
            this.healthy = healthy;
            this.message = message;
            this.statistics = statistics;
        }

        /**
         * Returns {@code true} if the pool was healthy at the time this snapshot was taken.
         *
         * @return {@code true} if healthy; {@code false} if the pool is uninitialised,
         *         closing, or unable to vend a connection
         */
        public boolean isHealthy() {
            return healthy;
        }

        /**
         * Returns a human-readable description of the health state.
         *
         * @return a short diagnostic message; never {@code null}
         */
        public String getMessage() {
            return message;
        }

        /**
         * Returns formatted pool statistics at the time of the health check, or
         * {@code null} if the pool was not available.
         *
         * @return pool statistics string, or {@code null}
         * @see HikariConfig#getPoolStatistics(com.zaxxer.hikari.HikariDataSource)
         */
        public String getStatistics() {
            return statistics;
        }

        @Override
        public String toString() {
            return String.format(
                "HealthStatus{healthy=%s, message='%s', statistics='%s'}",
                healthy,
                message,
                statistics
            );
        }
    }
}
