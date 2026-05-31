package com.snowedunderproductions.graphprobe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in test execution profile selected by the {@code ITEST_PROFILE} environment
 * variable or the {@code -Ptest.profile=fast} Gradle property (wired into a system
 * property by {@code build.gradle}).
 *
 * <p>The {@link #FAST} profile trims the most call-heavy test paths (iteration counts,
 * page caps, argument-provider row limits) so a developer can get a quicker feedback
 * loop on a single machine. The {@link #DEFAULT} profile preserves full coverage and
 * is the correct choice for CI pipelines.
 *
 * <p>The active profile is resolved once at class-load time and cached; there is
 * no per-test cost.  Resolution order:
 * <ol>
 *   <li>System property {@code ITEST_PROFILE}</li>
 *   <li>System property {@code test.profile}</li>
 *   <li>Environment variable {@code ITEST_PROFILE} (via {@link EnvConfig#get(String)})</li>
 * </ol>
 * Any value equal to {@code "fast"} (case-insensitive) activates {@link #FAST};
 * everything else defaults to {@link #DEFAULT}.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Guard a slow iteration loop
 * int iterations = TestProfile.iterations(200);
 *
 * // Clamp a SQL LIMIT in a provider
 * String limit = TestProfile.clampSqlLimit("300");
 *
 * // Conditionally skip expensive setup
 * if (!TestProfile.isFast()) {
 *     warmUpCache();
 * }
 * }</pre>
 *
 * @see EnvConfig
 * @author Paul Snow
 * @since 0.0.0
 */
public enum TestProfile {

	/** Full coverage — the default for CI and any unconfigured run. */
	DEFAULT,

	/** Reduced call volume — trims iterations, page caps, and row limits. */
	FAST;

	private static final Logger log = LoggerFactory.getLogger(TestProfile.class);

	private static final TestProfile CURRENT;

	static {
		String raw = System.getProperty("ITEST_PROFILE");
		if (raw == null || raw.isBlank()) {
			raw = System.getProperty("test.profile");
		}
		if (raw == null || raw.isBlank()) {
			raw = EnvConfig.get("ITEST_PROFILE");
		}
		CURRENT = "fast".equalsIgnoreCase(raw == null ? "" : raw.trim()) ? FAST : DEFAULT;
		log.info("Test profile: {} (ITEST_PROFILE={})", CURRENT, raw == null ? "<unset>" : raw);
	}

	/**
	 * Returns the active profile for this JVM.
	 *
	 * @return the resolved {@link TestProfile}; never {@code null}
	 */
	public static TestProfile current() {
		return CURRENT;
	}

	/**
	 * Returns {@code true} when the {@link #FAST} profile is active.
	 *
	 * @return {@code true} if the current profile is {@link #FAST}, {@code false} otherwise
	 */
	public static boolean isFast() {
		return CURRENT == FAST;
	}

	/**
	 * Clamps a page count to the fast-profile cap when active.
	 *
	 * @param defaultMaxPage the value used in the default profile
	 * @return {@code min(defaultMaxPage, 3)} in fast mode, otherwise {@code defaultMaxPage}
	 */
	public static int clampMaxPage(int defaultMaxPage) {
		return isFast() ? Math.min(defaultMaxPage, 3) : defaultMaxPage;
	}

	/**
	 * Clamps a numeric SQL {@code LIMIT} to the fast-profile cap when active. Returns
	 * the value as a String so it can be concatenated into SQL templates unchanged.
	 *
	 * @param defaultLimit the limit used in the default profile (e.g. {@code "300"})
	 * @return {@code "50"} in fast mode if {@code defaultLimit} is larger, otherwise {@code defaultLimit}
	 */
	public static String clampSqlLimit(String defaultLimit) {
		if (!isFast()) {
			return defaultLimit;
		}
		try {
			long requested = Long.parseLong(defaultLimit.trim());
			return Long.toString(Math.min(requested, 50L));
		} catch (NumberFormatException e) {
			return defaultLimit;
		}
	}

	/**
	 * Clamps a measured-iteration count to the fast-profile cap when active.
	 *
	 * @param defaultIterations the iteration count used in the default profile
	 * @return {@code min(defaultIterations, 5)} in fast mode, otherwise {@code defaultIterations}
	 */
	public static int iterations(int defaultIterations) {
		return isFast() ? Math.min(defaultIterations, 5) : defaultIterations;
	}

	/**
	 * Clamps a warm-up iteration count to the fast-profile cap when active.
	 *
	 * @param defaultWarmup the warm-up count used in the default profile
	 * @return {@code min(defaultWarmup, 1)} in fast mode, otherwise {@code defaultWarmup}
	 */
	public static int warmup(int defaultWarmup) {
		return isFast() ? Math.min(defaultWarmup, 1) : defaultWarmup;
	}
}
