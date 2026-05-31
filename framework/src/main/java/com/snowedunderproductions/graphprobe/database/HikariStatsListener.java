package com.snowedunderproductions.graphprobe.database;

import com.snowedunderproductions.graphprobe.config.EnvConfig;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

/**
 * JUnit Platform {@link TestExecutionListener} that drives {@link ConnectionPoolMonitor}
 * for the lifetime of a test plan, so connection-pool saturation and leaks surface in
 * the logs during a run.
 *
 * <p>This is a thin, opt-in bridge: it starts {@link ConnectionPoolMonitor} when the test
 * plan begins and stops it when the plan finishes. The monitor itself handles scheduling,
 * lazy initialisation (its health check no-ops until the pool is initialised) and graceful
 * shutdown, so registering this listener never forces a database connection for plans that
 * do not use the database.
 *
 * <h3>Opt-in registration</h3>
 * <p>This listener is intentionally <strong>not</strong> auto-registered by the framework.
 * To enable it in a consuming project, register it via a service file on the test
 * classpath:
 * <pre>{@code
 * # src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener
 * com.snowedunderproductions.graphprobe.database.HikariStatsListener
 * }</pre>
 *
 * <h3>Configuration</h3>
 * <ul>
 *   <li>{@code HIKARI_STATS_INTERVAL_SECONDS} &mdash; monitoring interval in seconds (default: 30).</li>
 * </ul>
 *
 * @author Paul Snow
 * @since 0.0.0
 * @see ConnectionPoolMonitor
 */
public class HikariStatsListener implements TestExecutionListener {

    private static final int DEFAULT_INTERVAL_SECONDS = 30;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        ConnectionPoolMonitor.start(
            EnvConfig.getInt(
                "HIKARI_STATS_INTERVAL_SECONDS",
                DEFAULT_INTERVAL_SECONDS
            )
        );
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        ConnectionPoolMonitor.stop();
    }
}
