package com.snowedunderproductions.graphprobe.database;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background monitor for HikariCP connection-pool health and performance.
 *
 * <p>When started, a single daemon thread runs at a configurable interval and:
 * <ul>
 *   <li>Checks the {@link DatabaseConnectionManager} health status and logs a warning
 *       or error if the pool is unhealthy.</li>
 *   <li>Warns when pool utilisation exceeds 80 % of capacity.</li>
 *   <li>Warns when threads are queuing for connections above a threshold.</li>
 *   <li>Warns when the connection-failure rate exceeds 5 %.</li>
 *   <li>Flags potential connection leaks when the active-connection count is high.</li>
 *   <li>Logs a full statistics summary every tenth check to reduce log noise.</li>
 * </ul>
 *
 * <p>All methods are static.  This class is not instantiated directly.
 * {@link HikariStatsListener} is the recommended way to drive the monitor during a
 * test run — it starts monitoring when the JUnit Platform test plan begins and stops
 * it when the plan finishes.
 *
 * <p>Direct usage is also straightforward:
 * <pre>{@code
 * // Start monitoring with the default 30-second interval:
 * ConnectionPoolMonitor.start();
 *
 * // Or start with a custom interval:
 * ConnectionPoolMonitor.start(60);
 *
 * // Stop monitoring explicitly:
 * ConnectionPoolMonitor.stop();
 * }</pre>
 *
 * <p>A JVM shutdown hook is registered automatically on {@link #start(int)} so the
 * monitor is always stopped cleanly even if {@link #stop()} is not called.
 *
 * @see HikariStatsListener
 * @see DatabaseConnectionManager
 */
public class ConnectionPoolMonitor {

	private static final Logger log = LoggerFactory.getLogger(
		ConnectionPoolMonitor.class
	);

	// Default monitoring configuration
	private static final int DEFAULT_MONITORING_INTERVAL_SECONDS = 30;
	private static final int DEFAULT_CONNECTION_LEAK_THRESHOLD = 5;
	private static final double DEFAULT_POOL_UTILIZATION_WARNING_THRESHOLD =
		0.8; // 80%
	private static final int DEFAULT_PENDING_CONNECTIONS_WARNING_THRESHOLD = 3;

	// Monitoring state
	private static final AtomicBoolean monitoringActive = new AtomicBoolean(
		false
	);
	private static volatile ScheduledExecutorService scheduler;
	private static volatile ScheduledFuture<?> monitoringTask;

	// Statistics tracking
	private static volatile long monitoringStartTime = 0;
	private static volatile long totalHealthChecks = 0;
	private static volatile long healthCheckFailures = 0;
	private static volatile long connectionLeakAlerts = 0;
	private static volatile long performanceAlerts = 0;

	/**
	 * Starts connection-pool monitoring with the default interval of 30 seconds.
	 *
	 * <p>If monitoring is already active this call is a no-op.
	 *
	 * @see #start(int)
	 */
	public static void start() {
		start(DEFAULT_MONITORING_INTERVAL_SECONDS);
	}

	/**
	 * Starts connection-pool monitoring with the specified interval.
	 *
	 * <p>The first health check runs immediately; subsequent checks are scheduled at
	 * {@code intervalSeconds} intervals.  A JVM shutdown hook is registered to stop
	 * the monitor when the process exits.  If monitoring is already active this call
	 * is a no-op.
	 *
	 * @param intervalSeconds the interval between health checks, in seconds; must be positive
	 */
	public static void start(int intervalSeconds) {
		if (monitoringActive.compareAndSet(false, true)) {
			log.info(
				"Starting connection pool monitoring with {}s interval",
				intervalSeconds
			);

			scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "ConnectionPoolMonitor");
				t.setDaemon(true); // Don't prevent JVM shutdown
				return t;
			});

			monitoringTask = scheduler.scheduleAtFixedRate(
				ConnectionPoolMonitor::performHealthCheck,
				0, // Start immediately
				intervalSeconds,
				TimeUnit.SECONDS
			);

			monitoringStartTime = System.currentTimeMillis();

			// Register shutdown hook
			Runtime.getRuntime()
				.addShutdownHook(
					new Thread(
						() -> {
							if (monitoringActive.get()) {
								log.info(
									"JVM shutdown detected, stopping connection pool monitoring"
								);
								stop();
							}
						},
						"ConnectionPoolMonitor-ShutdownHook"
					)
				);

			log.info("Connection pool monitoring started successfully");
		} else {
			log.debug("Connection pool monitoring is already active");
		}
	}

	/**
	 * Stops connection-pool monitoring and shuts down the background scheduler.
	 *
	 * <p>Waits up to 5 seconds for the scheduler to terminate gracefully, then forces
	 * termination.  Logs a summary of health checks and alerts before stopping.  If
	 * monitoring is not currently active this call is a no-op.
	 */
	public static void stop() {
		if (monitoringActive.compareAndSet(true, false)) {
			log.info("Stopping connection pool monitoring");

			if (monitoringTask != null) {
				monitoringTask.cancel(false);
				monitoringTask = null;
			}

			if (scheduler != null) {
				scheduler.shutdown();
				try {
					if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
						scheduler.shutdownNow();
					}
				} catch (InterruptedException e) {
					scheduler.shutdownNow();
					Thread.currentThread().interrupt();
				}
				scheduler = null;
			}

			logFinalStatistics();
			log.info("Connection pool monitoring stopped");
		}
	}

	/**
	 * Performs a single health check of the connection pool.
	 * This method is called periodically by the monitoring scheduler.
	 */
	private static void performHealthCheck() {
		try {
			totalHealthChecks++;

			DatabaseConnectionManager connectionManager =
				DatabaseConnectionManager.getInstance();

			if (!connectionManager.isInitialized()) {
				log.warn("Health check: Connection manager is not initialized");
				healthCheckFailures++;
				return;
			}

			// Get health status
			var healthStatus = connectionManager.getHealthStatus();
			String statistics = connectionManager.getStatistics();

			if (!healthStatus.isHealthy()) {
				log.error("Health check FAILED: {}", healthStatus.getMessage());
				log.error("Pool statistics: {}", statistics);
				healthCheckFailures++;
				return;
			}

			// Parse and analyze statistics
			PoolStatistics poolStats = parseStatistics(statistics);
			if (poolStats != null) {
				checkForPerformanceIssues(poolStats);
				checkForConnectionLeaks(poolStats);

				// Log detailed statistics every 10th check (to reduce log noise)
				if (totalHealthChecks % 10 == 0) {
					log.info(
						"Health check #{}: {}",
						totalHealthChecks,
						statistics
					);
					log.info(
						"Pool analysis: utilization={}%, leak_risk={}, pending_connections={}",
						poolStats.getUtilizationPercent(),
						poolStats.hasLeakRisk() ? "HIGH" : "LOW",
						poolStats.pendingConnections
					);
				}
			} else {
				log.debug(
					"Health check #{}: {}",
					totalHealthChecks,
					statistics
				);
			}
		} catch (Exception e) {
			log.error("Error during connection pool health check", e);
			healthCheckFailures++;
		}
	}

	/**
	 * Parses connection pool statistics string into structured data.
	 *
	 * @param statistics the statistics string from connection manager
	 * @return parsed statistics or null if parsing fails
	 */
	private static PoolStatistics parseStatistics(String statistics) {
		try {
			// Example format: "Pool Statistics [GraphQLTestPool]: Active=2, Idle=3, Total=5, Pending=0, Requested=100, Failed=1, Returned=99"

			int active = extractIntValue(statistics, "Active=");
			int total = extractIntValue(statistics, "Total=");
			int pending = extractIntValue(statistics, "Pending=");
			long requested = extractLongValue(statistics, "Requested=");
			long failed = extractLongValue(statistics, "Failed=");

			return new PoolStatistics(
				active,
				total,
				pending,
				requested,
				failed
			);
		} catch (Exception e) {
			log.debug("Failed to parse pool statistics: {}", statistics, e);
			return null;
		}
	}

	/**
	 * Extracts integer value from statistics string.
	 */
	private static int extractIntValue(String statistics, String key) {
		int startIndex = statistics.indexOf(key);
		if (startIndex == -1) return 0;

		startIndex += key.length();
		int endIndex = statistics.indexOf(',', startIndex);
		if (endIndex == -1) endIndex = statistics.length();

		String valueStr = statistics.substring(startIndex, endIndex).trim();
		return Integer.parseInt(valueStr);
	}

	/**
	 * Extracts long value from statistics string.
	 */
	private static long extractLongValue(String statistics, String key) {
		int startIndex = statistics.indexOf(key);
		if (startIndex == -1) return 0;

		startIndex += key.length();
		int endIndex = statistics.indexOf(',', startIndex);
		if (endIndex == -1) endIndex = statistics.length();

		String valueStr = statistics.substring(startIndex, endIndex).trim();
		return Long.parseLong(valueStr);
	}

	/**
	 * Checks for performance issues based on pool statistics.
	 */
	private static void checkForPerformanceIssues(PoolStatistics stats) {
		// Check pool utilization
		if (
			stats.getUtilizationPercent() >
			DEFAULT_POOL_UTILIZATION_WARNING_THRESHOLD * 100
		) {
			log.warn(
				"HIGH POOL UTILIZATION: {}% ({}/{} connections in use)",
				stats.getUtilizationPercent(),
				stats.activeConnections,
				stats.totalConnections
			);
			performanceAlerts++;
		}

		// Check pending connections
		if (
			stats.pendingConnections >
			DEFAULT_PENDING_CONNECTIONS_WARNING_THRESHOLD
		) {
			log.warn(
				"HIGH PENDING CONNECTIONS: {} threads waiting for connections",
				stats.pendingConnections
			);
			performanceAlerts++;
		}

		// Check failure rate
		if (stats.requestedConnections > 0) {
			double failureRate =
				(double) stats.failedConnections / stats.requestedConnections;
			if (failureRate > 0.05) { // 5% failure rate
				log.warn(
					"HIGH CONNECTION FAILURE RATE: {}% ({}/{} connections failed)",
					failureRate * 100,
					stats.failedConnections,
					stats.requestedConnections
				);
				performanceAlerts++;
			}
		}
	}

	/**
	 * Checks for potential connection leaks.
	 */
	private static void checkForConnectionLeaks(PoolStatistics stats) {
		if (stats.hasLeakRisk()) {
			log.warn(
				"POTENTIAL CONNECTION LEAK: {} active connections with low returned rate",
				stats.activeConnections
			);
			connectionLeakAlerts++;
		}
	}

	/**
	 * Logs final monitoring statistics.
	 */
	private static void logFinalStatistics() {
		if (monitoringStartTime > 0) {
			long totalRuntime =
				System.currentTimeMillis() - monitoringStartTime;
			double uptimeHours = totalRuntime / (1000.0 * 60.0 * 60.0);

			log.info("Connection Pool Monitoring Summary:");
			log.info("  Total Runtime: {} hours", uptimeHours);
			log.info(
				"  Health Checks: {} total, {} failures ({}% success rate)",
				totalHealthChecks,
				healthCheckFailures,
				(totalHealthChecks > 0
						? (1.0 -
							(double) healthCheckFailures / totalHealthChecks) *
						100
						: 0)
			);
			log.info(
				"  Alerts: {} connection leak warnings, {} performance warnings",
				connectionLeakAlerts,
				performanceAlerts
			);
		}
	}

	/**
	 * Returns a {@link MonitoringStatus} snapshot of the current monitoring state and
	 * accumulated statistics.
	 *
	 * @return a {@link MonitoringStatus} instance; never {@code null}
	 */
	public static MonitoringStatus getStatus() {
		long runtime = monitoringStartTime > 0
			? System.currentTimeMillis() - monitoringStartTime
			: 0;

		return new MonitoringStatus(
			monitoringActive.get(),
			runtime,
			totalHealthChecks,
			healthCheckFailures,
			connectionLeakAlerts,
			performanceAlerts
		);
	}

	/**
	 * Returns {@code true} if the background monitoring scheduler is currently running.
	 *
	 * @return {@code true} if monitoring is active; {@code false} otherwise
	 */
	public static boolean isActive() {
		return monitoringActive.get();
	}

	/**
	 * Container for parsed pool statistics.
	 */
	private static class PoolStatistics {

		final int activeConnections;
		final int totalConnections;
		final int pendingConnections;
		final long requestedConnections;
		final long failedConnections;

		PoolStatistics(
			int active,
			int total,
			int pending,
			long requested,
			long failed
		) {
			this.activeConnections = active;
			this.totalConnections = total;
			this.pendingConnections = pending;
			this.requestedConnections = requested;
			this.failedConnections = failed;
		}

		double getUtilizationPercent() {
			return totalConnections > 0
				? ((double) activeConnections / totalConnections) * 100
				: 0;
		}

		boolean hasLeakRisk() {
			return activeConnections >= DEFAULT_CONNECTION_LEAK_THRESHOLD;
		}
	}

	/**
	 * Immutable snapshot of the monitoring state and accumulated statistics at a point in
	 * time.
	 *
	 * <p>Instances are returned by {@link ConnectionPoolMonitor#getStatus()}.
	 *
	 * @see ConnectionPoolMonitor#getStatus()
	 */
	public static class MonitoringStatus {

		private final boolean active;
		private final long runtimeMs;
		private final long totalHealthChecks;
		private final long healthCheckFailures;
		private final long connectionLeakAlerts;
		private final long performanceAlerts;

		public MonitoringStatus(
			boolean active,
			long runtimeMs,
			long totalHealthChecks,
			long healthCheckFailures,
			long connectionLeakAlerts,
			long performanceAlerts
		) {
			this.active = active;
			this.runtimeMs = runtimeMs;
			this.totalHealthChecks = totalHealthChecks;
			this.healthCheckFailures = healthCheckFailures;
			this.connectionLeakAlerts = connectionLeakAlerts;
			this.performanceAlerts = performanceAlerts;
		}

		/**
		 * Returns {@code true} if the monitoring scheduler is running at the time this
		 * snapshot was taken.
		 *
		 * @return {@code true} if active
		 */
		public boolean isActive() {
			return active;
		}

		/**
		 * Returns the total wall-clock time in milliseconds since monitoring was started,
		 * or {@code 0} if monitoring has not yet been started.
		 *
		 * @return elapsed monitoring time in milliseconds
		 */
		public long getRuntimeMs() {
			return runtimeMs;
		}

		/**
		 * Returns the total number of health-check cycles completed since monitoring started.
		 *
		 * @return total health-check count
		 */
		public long getTotalHealthChecks() {
			return totalHealthChecks;
		}

		/**
		 * Returns the number of health-check cycles that encountered an error or found the
		 * pool to be unhealthy.
		 *
		 * @return number of failed health checks
		 */
		public long getHealthCheckFailures() {
			return healthCheckFailures;
		}

		/**
		 * Returns the number of times a potential connection leak was detected (active
		 * connection count at or above the leak threshold).
		 *
		 * @return cumulative connection-leak alert count
		 */
		public long getConnectionLeakAlerts() {
			return connectionLeakAlerts;
		}

		/**
		 * Returns the number of performance warnings raised (high pool utilisation, high
		 * pending-connection count, or elevated connection-failure rate).
		 *
		 * @return cumulative performance-alert count
		 */
		public long getPerformanceAlerts() {
			return performanceAlerts;
		}

		@Override
		public String toString() {
			return String.format(
				"MonitoringStatus{active=%s, runtime=%dms, checks=%d, failures=%d, leakAlerts=%d, perfAlerts=%d}",
				active,
				runtimeMs,
				totalHealthChecks,
				healthCheckFailures,
				connectionLeakAlerts,
				performanceAlerts
			);
		}
	}
}
