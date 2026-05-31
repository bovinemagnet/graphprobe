package com.snowedunderproductions.graphprobe.client;

import com.snowedunderproductions.graphprobe.config.EnvConfig;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Process-wide cap on the number of in-flight GraphQL requests issued by the
 * integration-test suite. Acts as a single chokepoint for backpressure so the
 * local or shared GraphQL server is not saturated by aggressive parallel test
 * execution, regardless of how many Gradle forks or JUnit threads are running.
 *
 * <p>Sized via the {@code GRAPHQL_MAX_CONCURRENT} environment variable
 * (default {@value #DEFAULT_MAX_CONCURRENT}). Set to {@code 0} to disable.
 *
 * @author Paul Snow
 * @since 0.0.0
 */
public final class GraphQLRequestThrottle {

	private static final Logger log = LoggerFactory.getLogger(GraphQLRequestThrottle.class);

	/** Default permit count when {@code GRAPHQL_MAX_CONCURRENT} is unset. */
	public static final int DEFAULT_MAX_CONCURRENT = 16;

	/** Maximum time to wait for a permit before failing fast. */
	private static final long ACQUIRE_TIMEOUT_SECONDS = 120;

	private static final Semaphore PERMITS;
	private static final boolean ENABLED;
	private static final int CONFIGURED_PERMITS;

	static {
		CONFIGURED_PERMITS = EnvConfig.getInt("GRAPHQL_MAX_CONCURRENT", DEFAULT_MAX_CONCURRENT);
		ENABLED = CONFIGURED_PERMITS > 0;
		PERMITS = ENABLED ? new Semaphore(CONFIGURED_PERMITS, true) : null;
		if (ENABLED) {
			log.info("GraphQL request throttle enabled: {} permits (GRAPHQL_MAX_CONCURRENT)", CONFIGURED_PERMITS);
		} else {
			log.info("GraphQL request throttle disabled (GRAPHQL_MAX_CONCURRENT={})", CONFIGURED_PERMITS);
		}
	}

	private GraphQLRequestThrottle() {}

	/**
	 * Acquires a permit, blocking up to {@value #ACQUIRE_TIMEOUT_SECONDS} seconds.
	 *
	 * @return {@code true} if a permit was acquired (or throttling is disabled);
	 *         {@code false} if the acquire timed out.
	 * @throws InterruptedException if the current thread is interrupted while waiting
	 */
	public static boolean acquire() throws InterruptedException {
		if (!ENABLED) {
			return true;
		}
		long startNanos = System.nanoTime();
		boolean acquired = PERMITS.tryAcquire(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		GraphQLRequestMetrics.recordAcquireWait(System.nanoTime() - startNanos);
		return acquired;
	}

	/**
	 * Releases a previously acquired permit. No-op when throttling is disabled.
	 */
	public static void release() {
		if (ENABLED) {
			PERMITS.release();
		}
	}

	/**
	 * Returns the configured permit count.
	 *
	 * @return the number of permits the semaphore was initialised with, or {@code 0}
	 *         when throttling is disabled
	 */
	public static int configuredPermits() {
		return ENABLED ? CONFIGURED_PERMITS : 0;
	}

	/**
	 * Returns the number of permits currently available (i.e. requests that could
	 * be started immediately without blocking).
	 *
	 * @return available permit count, or {@link Integer#MAX_VALUE} when throttling is
	 *         disabled
	 */
	public static int availablePermits() {
		return ENABLED ? PERMITS.availablePermits() : Integer.MAX_VALUE;
	}
}
