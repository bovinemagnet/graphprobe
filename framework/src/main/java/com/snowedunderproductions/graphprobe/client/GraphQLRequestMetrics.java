package com.snowedunderproductions.graphprobe.client;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import com.snowedunderproductions.graphprobe.config.EnvConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects and publishes runtime metrics for the GraphQL request layer so test
 * runs surface overload conditions visibly rather than silently timing out.
 *
 * <p>Tracked dimensions:
 * <ul>
 *   <li><strong>calls</strong> — total GraphQL requests executed</li>
 *   <li><strong>rate</strong> — overall and rolling-window calls per second</li>
 *   <li><strong>permit wait</strong> — time spent blocked on
 *       {@link GraphQLRequestThrottle} (average + max)</li>
 *   <li><strong>request duration</strong> — wall time of the HTTP exchange
 *       including retries (average + max)</li>
 *   <li><strong>in-flight</strong> — requests currently holding a throttle permit</li>
 *   <li><strong>errors / retries</strong> — counts of failed and retried calls</li>
 * </ul>
 *
 * <p>A periodic summary is emitted at INFO every {@value #REPORT_INTERVAL_SECONDS}
 * seconds; a final summary is logged on JVM shutdown.
 *
 * @author Paul Snow
 * @since 0.0.0
 */
public final class GraphQLRequestMetrics {

	private static final Logger log = LoggerFactory.getLogger(GraphQLRequestMetrics.class);

	/** How often the periodic summary is logged. */
	public static final long REPORT_INTERVAL_SECONDS = 30L;

	private static final LongAdder CALLS = new LongAdder();
	private static final LongAdder ERRORS = new LongAdder();
	private static final LongAdder RETRIES = new LongAdder();
	private static final AtomicLong TOTAL_WAIT_NANOS = new AtomicLong(0);
	private static final AtomicLong TOTAL_DURATION_NANOS = new AtomicLong(0);
	private static final AtomicLong MAX_WAIT_NANOS = new AtomicLong(0);
	private static final AtomicLong MAX_DURATION_NANOS = new AtomicLong(0);
	private static final long STARTED_AT_MILLIS = System.currentTimeMillis();

	/** Per-test-class buckets, keyed by fully-qualified class name. */
	private static final ConcurrentMap<String, ClassStats> BY_CLASS = new ConcurrentHashMap<>();

	/**
	 * StackWalker used to attribute each call to the originating test class.
	 * No class-reference retention needed — we only inspect class names.
	 */
	private static final StackWalker STACK_WALKER = StackWalker.getInstance(Collections.emptySet(), 32);

	/**
	 * Optional fully-qualified-name prefix a stack frame must start with to be
	 * attributed to a test class. Configured via {@code GRAPHQL_TEST_PACKAGE_PREFIX}
	 * (default: empty, i.e. any class whose simple name ends in Test/IT).
	 */
	private static final String TEST_PACKAGE_PREFIX = EnvConfig.get("GRAPHQL_TEST_PACKAGE_PREFIX", "");

	private static volatile ScheduledExecutorService scheduler;
	private static volatile long lastReportMillis = STARTED_AT_MILLIS;
	private static volatile long lastReportCalls = 0L;

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(GraphQLRequestMetrics::logFinalSummary, "graphql-metrics-shutdown"));
	}

	private GraphQLRequestMetrics() {}

	/** Records the time spent waiting for a {@link GraphQLRequestThrottle} permit. */
	public static void recordAcquireWait(long waitNanos) {
		TOTAL_WAIT_NANOS.addAndGet(waitNanos);
		accumulateMax(MAX_WAIT_NANOS, waitNanos);
		ensureScheduler();
	}

	/**
	 * Records a completed call, attributing it to the calling test class via stack
	 * walking. Safe for synchronous callers (e.g. {@code SimpleGraphQLClient}); the
	 * reactive path should use {@link #recordCall(long, boolean, String)} with a
	 * pre-captured class name because the terminal callback runs on a Netty thread
	 * whose stack does not include the test frame.
	 */
	public static void recordCall(long durationNanos, boolean success) {
		recordCall(durationNanos, success, detectTestClass());
	}

	/**
	 * Records a completed call, attributing it to an explicit test class.
	 *
	 * @param testClass fully-qualified class name to bucket under, or {@code null} to
	 *                  skip per-class attribution
	 */
	public static void recordCall(long durationNanos, boolean success, String testClass) {
		CALLS.increment();
		if (!success) {
			ERRORS.increment();
		}
		TOTAL_DURATION_NANOS.addAndGet(durationNanos);
		accumulateMax(MAX_DURATION_NANOS, durationNanos);
		if (testClass != null) {
			ClassStats stats = BY_CLASS.computeIfAbsent(testClass, k -> new ClassStats());
			stats.calls.increment();
			if (!success) {
				stats.errors.increment();
			}
			stats.totalDurationNanos.addAndGet(durationNanos);
			accumulateMax(stats.maxDurationNanos, durationNanos);
		}
		ensureScheduler();
	}

	/**
	 * Exposes the stack-walking attribution helper so callers on the reactive path
	 * can capture the test class on the subscribing thread and pass it back via
	 * {@link #recordCall(long, boolean, String)} once the request terminates.
	 *
	 * @return the originating test class FQCN, or {@code null} when no test frame
	 *         is on the current call stack.
	 */
	public static String currentTestClass() {
		return detectTestClass();
	}

	/**
	 * Walks the live call stack to attribute the request to the originating test class.
	 *
	 * @return the fully-qualified name of the first frame in the configured {@code GRAPHQL_TEST_PACKAGE_PREFIX}
	 *         whose simple name ends in {@code Test} or {@code IT}, or {@code null} if no
	 *         such frame is on the stack (e.g. internal warm-up calls).
	 */
	private static String detectTestClass() {
		return STACK_WALKER.walk(stream -> stream
			.map(StackWalker.StackFrame::getClassName)
			.filter(name -> name.startsWith(TEST_PACKAGE_PREFIX) && (name.endsWith("Test") || name.endsWith("IT")))
			.findFirst()
			.orElse(null));
	}

	/**
	 * Returns a one-line human-readable summary of HTTP calls attributed to the
	 * given test class, or {@code null} if no calls have been recorded for it.
	 * Called by {@link GraphQLMetricsExtension} at the end of each test class.
	 *
	 * @param className the fully-qualified test class name to summarise
	 * @return a one-line summary string, or {@code null} when no data is available
	 */
	public static String formatClassSummary(String className) {
		ClassStats stats = BY_CLASS.get(className);
		if (stats == null) {
			return null;
		}
		long calls = stats.calls.sum();
		if (calls == 0) {
			return null;
		}
		long errs = stats.errors.sum();
		long totalDurNanos = stats.totalDurationNanos.get();
		long avgMs = TimeUnit.NANOSECONDS.toMillis(totalDurNanos) / calls;
		long maxMs = TimeUnit.NANOSECONDS.toMillis(stats.maxDurationNanos.get());
		return String.format("%s — calls=%d, errors=%d, avgDuration=%dms, maxDuration=%dms", className, calls, errs, avgMs, maxMs);
	}

	/**
	 * Per-test-class accumulator holding call counts, error counts, and duration
	 * statistics. Instances are stored in {@link #BY_CLASS} keyed by fully-qualified
	 * class name and updated concurrently by multiple test threads.
	 */
	private static final class ClassStats {

		final LongAdder calls = new LongAdder();
		final LongAdder errors = new LongAdder();
		final AtomicLong totalDurationNanos = new AtomicLong();
		final AtomicLong maxDurationNanos = new AtomicLong();
	}

	/**
	 * Records that a retry attempt was made for a transient failure.
	 * Called by {@link SimpleGraphQLClient} before each retry so the
	 * {@link #formatSummary()} report reflects the total retry count.
	 */
	public static void recordRetry() {
		RETRIES.increment();
	}

	private static void accumulateMax(AtomicLong target, long value) {
		long current;
		do {
			current = target.get();
			if (value <= current) {
				return;
			}
		} while (!target.compareAndSet(current, value));
	}

	private static void ensureScheduler() {
		if (scheduler != null) {
			return;
		}
		synchronized (GraphQLRequestMetrics.class) {
			if (scheduler != null) {
				return;
			}
			ScheduledExecutorService s = Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "graphql-metrics-reporter");
				t.setDaemon(true);
				return t;
			});
			s.scheduleAtFixedRate(GraphQLRequestMetrics::logPeriodicSummary, REPORT_INTERVAL_SECONDS, REPORT_INTERVAL_SECONDS, TimeUnit.SECONDS);
			scheduler = s;
		}
	}

	/**
	 * Returns a human-readable single-line snapshot of the current aggregate metric
	 * values covering all test classes. Safe to call from any thread at any time.
	 *
	 * @return a formatted summary string including call count, error/retry counts,
	 *         overall rate, permit-wait statistics, request-duration statistics, and
	 *         current in-flight count
	 */
	public static String formatSummary() {
		long now = System.currentTimeMillis();
		long totalCalls = CALLS.sum();
		long elapsedMs = Math.max(1L, now - STARTED_AT_MILLIS);
		double overallCps = totalCalls * 1000.0 / elapsedMs;
		long avgWaitMs = totalCalls > 0 ? TimeUnit.NANOSECONDS.toMillis(TOTAL_WAIT_NANOS.get()) / totalCalls : 0L;
		long avgDurMs = totalCalls > 0 ? TimeUnit.NANOSECONDS.toMillis(TOTAL_DURATION_NANOS.get()) / totalCalls : 0L;
		long maxWaitMs = TimeUnit.NANOSECONDS.toMillis(MAX_WAIT_NANOS.get());
		long maxDurMs = TimeUnit.NANOSECONDS.toMillis(MAX_DURATION_NANOS.get());
		int permits = GraphQLRequestThrottle.configuredPermits();
		int avail = GraphQLRequestThrottle.availablePermits();
		String inFlight = permits == 0 ? "n/a" : (permits - avail) + "/" + permits;
		return String.format(
			"GraphQL metrics — calls=%d, errors=%d, retries=%d, rate=%.2f/s, avgWait=%dms (max %dms), avgDuration=%dms (max %dms), inFlight=%s",
			totalCalls,
			ERRORS.sum(),
			RETRIES.sum(),
			overallCps,
			avgWaitMs,
			maxWaitMs,
			avgDurMs,
			maxDurMs,
			inFlight
		);
	}

	private static synchronized void logPeriodicSummary() {
		long now = System.currentTimeMillis();
		long totalCalls = CALLS.sum();
		long deltaCalls = totalCalls - lastReportCalls;
		if (deltaCalls == 0) {
			// Nothing happened in the window — skip the log to avoid noise.
			return;
		}
		long deltaMs = Math.max(1L, now - lastReportMillis);
		double windowCps = deltaCalls * 1000.0 / deltaMs;
		lastReportMillis = now;
		lastReportCalls = totalCalls;
		log.info("{} | window={}calls/{}s ({}/s)", formatSummary(), deltaCalls, deltaMs / 1000, String.format("%.2f", windowCps));
	}

	private static void logFinalSummary() {
		try {
			if (scheduler != null) {
				scheduler.shutdownNow();
			}
		} catch (Exception ignored) {
			// nothing useful to do during shutdown
		}
		if (CALLS.sum() == 0) {
			return;
		}
		log.info("FINAL {}", formatSummary());
	}
}
