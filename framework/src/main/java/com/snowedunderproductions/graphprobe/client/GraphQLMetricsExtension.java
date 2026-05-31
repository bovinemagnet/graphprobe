package com.snowedunderproductions.graphprobe.client;

import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.model.FixtureResult;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.TestResultContainer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit 5 extension that publishes the per-class GraphQL request summary at the
 * end of each test class. Reads the bucket populated by
 * {@link GraphQLRequestMetrics#recordCall(long, boolean)} (attributed via
 * stack-walking) and attaches it to the Allure report so the heaviest test
 * classes are visible per run.
 *
 * <p>Can be auto-registered via
 * {@code META-INF/services/org.junit.jupiter.api.extension.Extension} combined
 * with {@code junit.jupiter.extensions.autodetection.enabled=true} in
 * {@code junit-platform.properties}, so every test class receives the
 * {@code @AfterAll} hook automatically. Alternatively, register explicitly with
 * {@code @ExtendWith(GraphQLMetricsExtension.class)} on individual test classes.
 *
 * <p>The attachment is emitted via a synthetic Allure container with a tear-down
 * fixture rather than {@link Allure#addAttachment(String, String, String, String)}.
 * That convenience method requires an active test case, but {@code afterAll} runs
 * after the last test in the class has stopped — Allure would log
 * {@code "Could not add attachment: no test is running"} at ERROR level. Going
 * through {@link AllureLifecycle#startTestContainer} /
 * {@code startTearDownFixture} gives the attachment an explicit parent context and
 * surfaces it in the report as an "After All" fixture under the class.
 *
 * @see GraphQLRequestMetrics
 * @author Paul Snow
 * @since 0.0.0
 */
public class GraphQLMetricsExtension implements AfterAllCallback {

	private static final Logger log = LoggerFactory.getLogger(GraphQLMetricsExtension.class);

	/**
	 * Invoked by JUnit 5 after all tests in the class have run. Retrieves the
	 * per-class metrics summary from {@link GraphQLRequestMetrics#formatClassSummary(String)}
	 * and attaches it to the Allure report as a tear-down fixture. No-ops silently
	 * when no calls were recorded for the class or when Allure is not active.
	 *
	 * @param context the current extension context provided by the JUnit 5 framework
	 */
	@Override
	public void afterAll(ExtensionContext context) {
		String className = context.getRequiredTestClass().getName();
		String simpleName = context.getRequiredTestClass().getSimpleName();
		String summary = GraphQLRequestMetrics.formatClassSummary(className);
		if (summary == null) {
			return;
		}
		log.info("[per-class metrics] {}", summary);
		try {
			AllureLifecycle lifecycle = Allure.getLifecycle();
			String containerUuid = UUID.randomUUID().toString();
			TestResultContainer container = new TestResultContainer()
				.setUuid(containerUuid)
				.setName("GraphQL metrics: " + simpleName);
			lifecycle.startTestContainer(container);

			String fixtureUuid = UUID.randomUUID().toString();
			FixtureResult fixture = new FixtureResult()
				.setName("GraphQL request metrics")
				.setStatus(Status.PASSED);
			lifecycle.startTearDownFixture(containerUuid, fixtureUuid, fixture);

			lifecycle.addAttachment(
				"GraphQL request metrics (" + simpleName + ")",
				"text/plain",
				".txt",
				summary.getBytes(StandardCharsets.UTF_8));

			lifecycle.stopFixture(fixtureUuid);
			lifecycle.stopTestContainer(containerUuid);
			lifecycle.writeTestContainer(containerUuid);
		} catch (Throwable t) {
			// Allure may not be active for every runner — never disrupt the test.
			log.debug("Failed to attach metrics to Allure: {}", t.getMessage());
		}
	}
}
