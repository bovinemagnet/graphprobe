package com.snowedunderproductions.graphprobe.database;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit 5 extension that manages the HikariCP connection-pool lifecycle for the full
 * duration of a test run.
 *
 * <p>The extension hooks into the JUnit 5 {@link BeforeAllCallback} and
 * {@link AfterAllCallback} callbacks.  On the first {@code beforeAll} invocation it:
 * <ol>
 *   <li>Calls {@link DatabaseConnectionManager#initialize()} to start the pool.</li>
 *   <li>Executes a validation query ({@code SELECT current_timestamp}) to verify
 *       real connectivity before any test runs.</li>
 *   <li>Registers a JVM shutdown hook that gracefully drains the pool with a 10-second
 *       timeout when the process exits.</li>
 * </ol>
 *
 * <p>Subsequent {@code beforeAll} calls from other test classes are no-ops because
 * the pool is shared via the {@link DatabaseConnectionManager} singleton.  After each
 * test class, {@code afterAll} logs pool statistics and warns about unreturned
 * connections but does <em>not</em> close the pool (the shutdown hook handles that).
 *
 * <h3>Registration</h3>
 * <p>Annotate individual test classes:
 * <pre>{@code
 * @ExtendWith(DatabaseTestLifecycle.class)
 * class MyDatabaseTests { ... }
 * }</pre>
 *
 * <p>Or enable global auto-detection in {@code src/test/resources/junit-platform.properties}:
 * <pre>{@code
 * junit.jupiter.extensions.autodetection.enabled=true
 * }</pre>
 *
 * @see DatabaseConnectionManager
 * @see HikariConfig
 */
public class DatabaseTestLifecycle
	implements BeforeAllCallback, AfterAllCallback {

	private static final Logger log = LoggerFactory.getLogger(
		DatabaseTestLifecycle.class
	);

	// Shared state across all test classes
	private static final AtomicBoolean initialized = new AtomicBoolean(false);
	private static final AtomicBoolean shutdownRegistered = new AtomicBoolean(
		false
	);
	private static final Object initLock = new Object();

	// Statistics tracking
	private static volatile long initializationStartTime = 0;
	private static volatile long initializationEndTime = 0;

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		if (initialized.get()) {
			log.debug(
				"Database connection pool already initialized for test class: {}",
				context.getRequiredTestClass().getSimpleName()
			);
			return;
		}

		synchronized (initLock) {
			if (initialized.get()) {
				return; // Double-check after acquiring lock
			}

			log.info(
				"Initializing database connection pool for test execution"
			);
			initializationStartTime = System.currentTimeMillis();

			try {
				DatabaseConnectionManager connectionManager =
					DatabaseConnectionManager.getInstance();

				// Initialize with default configuration
				connectionManager.initialize();

				// Validate connectivity
				validateDatabaseConnectivity(connectionManager);

				// Register shutdown hook if not already done
				registerShutdownHook();

				initialized.set(true);
				initializationEndTime = System.currentTimeMillis();

				long initDuration =
					initializationEndTime - initializationStartTime;
				log.info(
					"Database connection pool initialized successfully in {}ms",
					initDuration
				);
				log.info(
					"Initial pool statistics: {}",
					connectionManager.getStatistics()
				);
			} catch (Exception e) {
				log.error("Failed to initialize database connection pool", e);
				throw new RuntimeException(
					"Database initialization failed for tests",
					e
				);
			}
		}
	}

	@Override
	public void afterAll(ExtensionContext context) throws Exception {
		// Don't shut down after each test class - let the shutdown hook handle it
		// This allows connection pool sharing across multiple test classes

		if (initialized.get()) {
			DatabaseConnectionManager connectionManager =
				DatabaseConnectionManager.getInstance();

			// Log final statistics for monitoring
			log.info(
				"Test class {} completed. Pool statistics: {}",
				context.getRequiredTestClass().getSimpleName(),
				connectionManager.getStatistics()
			);

			// Check for connection leaks
			checkForConnectionLeaks(connectionManager);
		}
	}

	/**
	 * Validates that the database connection pool is operational by executing a lightweight
	 * probe query ({@code SELECT current_timestamp}).
	 *
	 * @param connectionManager the initialised connection manager to probe
	 * @throws RuntimeException if the health check fails or the probe query cannot be executed
	 */
	private void validateDatabaseConnectivity(
		DatabaseConnectionManager connectionManager
	) throws Exception {
		log.debug("Validating database connectivity");

		var healthStatus = connectionManager.getHealthStatus();
		if (!healthStatus.isHealthy()) {
			throw new RuntimeException(
				"Database health check failed: " + healthStatus.getMessage()
			);
		}

		// Test connection acquisition and release
		try (var connection = connectionManager.getConnection()) {
			var statement = connection.createStatement();
			var resultSet = statement.executeQuery(
				"SELECT current_timestamp as test_time"
			);

			if (resultSet.next()) {
				log.debug(
					"Database connectivity validated successfully at: {}",
					resultSet.getTimestamp("test_time")
				);
			} else {
				throw new RuntimeException(
					"Database validation query returned no results"
				);
			}
		}
	}

	/**
	 * Registers a JVM shutdown hook that gracefully closes the connection pool with a
	 * 10-second timeout.  The hook is registered at most once, regardless of how many
	 * test classes use this extension.
	 */
	private void registerShutdownHook() {
		if (shutdownRegistered.compareAndSet(false, true)) {
			Runtime.getRuntime()
				.addShutdownHook(
					new Thread(
						() -> {
							if (initialized.get()) {
								log.info(
									"JVM shutdown detected, closing database connection pool"
								);

								try {
									DatabaseConnectionManager connectionManager =
										DatabaseConnectionManager.getInstance();

									// Log final statistics
									log.info(
										"Final pool statistics: {}",
										connectionManager.getStatistics()
									);

									// Graceful shutdown with 10 second timeout
									connectionManager.shutdown(10);

									long totalRuntime =
										System.currentTimeMillis() -
										initializationStartTime;
									log.info(
										"Database connection pool closed successfully. Total runtime: {}ms",
										totalRuntime
									);
								} catch (Exception e) {
									log.error(
										"Error during database connection pool shutdown",
										e
									);
								}
							}
						},
						"DatabaseTestLifecycle-ShutdownHook"
					)
				);

			log.debug("Shutdown hook registered for database connection pool");
		}
	}

	/**
	 * Inspects the current pool statistics for unreturned connections and logs a warning
	 * if any active connections remain after a test class has completed.
	 *
	 * @param connectionManager the connection manager whose statistics are inspected
	 */
	private void checkForConnectionLeaks(
		DatabaseConnectionManager connectionManager
	) {
		try {
			String statistics = connectionManager.getStatistics();
			log.debug("Checking for connection leaks: {}", statistics);

			// Parse statistics to check for potential issues
			if (
				statistics.contains("Active=") && statistics.contains("Total=")
			) {
				// Extract active connection count
				String activeStr = statistics.substring(
					statistics.indexOf("Active=") + 7
				);
				activeStr = activeStr.substring(0, activeStr.indexOf(','));

				try {
					int activeConnections = Integer.parseInt(activeStr.trim());
					if (activeConnections > 0) {
						log.warn(
							"Potential connection leak detected: {} active connections remain after test class completion",
							activeConnections
						);
						log.warn("Full statistics: {}", statistics);
					}
				} catch (NumberFormatException e) {
					log.debug(
						"Could not parse active connection count from statistics"
					);
				}
			}
		} catch (Exception e) {
			log.debug("Error checking for connection leaks", e);
		}
	}

	/**
	 * Returns a {@link LifecycleStatus} snapshot describing whether the pool is
	 * initialised and healthy, plus runtime duration and pool statistics.
	 *
	 * @return a {@link LifecycleStatus} instance; never {@code null}
	 */
	public static LifecycleStatus getStatus() {
		if (!initialized.get()) {
			return new LifecycleStatus(false, "Not initialized", 0, null);
		}

		DatabaseConnectionManager connectionManager =
			DatabaseConnectionManager.getInstance();
		var healthStatus = connectionManager.getHealthStatus();
		long runtime = initializationEndTime > 0
			? System.currentTimeMillis() - initializationStartTime
			: 0;

		return new LifecycleStatus(
			healthStatus.isHealthy(),
			healthStatus.getMessage(),
			runtime,
			connectionManager.getStatistics()
		);
	}

	/**
	 * Immediately closes the database connection pool without waiting for active
	 * connections to finish.
	 *
	 * <p>This method should only be used in emergency situations or framework-level
	 * teardown where a clean {@link DatabaseConnectionManager#shutdown(int)} is not
	 * feasible.  Active connections may be interrupted.
	 *
	 * @see DatabaseConnectionManager#forceShutdown()
	 */
	public static void forceShutdown() {
		if (initialized.get()) {
			log.warn("Force shutdown of database connection pool requested");

			try {
				DatabaseConnectionManager.getInstance().forceShutdown();
				initialized.set(false);
				log.info("Database connection pool force shutdown completed");
			} catch (Exception e) {
				log.error("Error during force shutdown", e);
			}
		}
	}

	/**
	 * Immutable snapshot of the database test-lifecycle state at a point in time.
	 *
	 * <p>Instances are returned by {@link DatabaseTestLifecycle#getStatus()}.
	 *
	 * @see DatabaseTestLifecycle#getStatus()
	 */
	public static class LifecycleStatus {

		private final boolean healthy;
		private final String message;
		private final long runtimeMs;
		private final String statistics;

		public LifecycleStatus(
			boolean healthy,
			String message,
			long runtimeMs,
			String statistics
		) {
			this.healthy = healthy;
			this.message = message;
			this.runtimeMs = runtimeMs;
			this.statistics = statistics;
		}

		/**
		 * Returns {@code true} if the pool was healthy at the time this snapshot was taken.
		 *
		 * @return {@code true} if healthy; {@code false} if the pool is not initialised or
		 *         unable to vend a connection
		 */
		public boolean isHealthy() {
			return healthy;
		}

		/**
		 * Returns a human-readable description of the lifecycle state.
		 *
		 * @return a short diagnostic message from the underlying
		 *         {@link DatabaseConnectionManager.HealthStatus}; never {@code null}
		 */
		public String getMessage() {
			return message;
		}

		/**
		 * Returns the total wall-clock time in milliseconds from when the pool was
		 * initialised to the time this snapshot was taken, or {@code 0} if the pool
		 * has not been initialised.
		 *
		 * @return elapsed runtime in milliseconds
		 */
		public long getRuntimeMs() {
			return runtimeMs;
		}

		/**
		 * Returns formatted pool statistics at the time of this snapshot, or {@code null}
		 * if the pool was not available.
		 *
		 * @return pool statistics string, or {@code null}
		 */
		public String getStatistics() {
			return statistics;
		}

		@Override
		public String toString() {
			return String.format(
				"LifecycleStatus{healthy=%s, message='%s', runtime=%dms, statistics='%s'}",
				healthy,
				message,
				runtimeMs,
				statistics
			);
		}
	}
}
