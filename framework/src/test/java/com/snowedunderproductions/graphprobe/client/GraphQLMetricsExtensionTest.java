package com.snowedunderproductions.graphprobe.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link GraphQLMetricsExtension}.
 *
 * <p>Specifically guards against the regression where {@code afterAll} called
 * {@link io.qameta.allure.Allure#addAttachment(String, String, String, String)}
 * outside an active test context, causing Allure to log
 * {@code "Could not add attachment: no test is running"} at ERROR for every
 * test class that recorded a GraphQL call.
 *
 * @author Paul Snow
 * @since 0.0.0
 */
@Epic("GraphQL Integration Tests")
@Feature("Test Utilities")
@Owner("Paul Snow")
class GraphQLMetricsExtensionTest {

	@Story("Allure metrics attachment")
	@Description("After-all hook publishes the per-class metrics summary via the Allure lifecycle without triggering the "
			+ "'no test is running' ERROR that Allure logs when addAttachment is called outside an active test context.")
	@Severity(SeverityLevel.NORMAL)
	@Test
	@DisplayName("afterAll attaches metrics without 'no test is running' ERROR")
	void afterAllAttachesMetricsWithoutAllureError() {
		// Pre-populate the metrics bucket for a class name we control.
		Class<?> sampleClass = SampleAttributedTest.class;
		GraphQLRequestMetrics.recordCall(50_000_000L, true, sampleClass.getName());

		// Sanity check: the summary must exist, otherwise the extension returns early
		// and the attachment code path isn't exercised.
		assertThat(GraphQLRequestMetrics.formatClassSummary(sampleClass.getName())).isNotNull();

		// Capture events from Allure's internal lifecycle logger — that's where the
		// regression "Could not add attachment: no test is running" originates.
		// ERROR events always propagate to attached appenders regardless of the
		// effective logger level, so we don't need to override it.
		Logger allureLifecycleLogger = (Logger) LoggerFactory.getLogger("io.qameta.allure.AllureLifecycle");
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		allureLifecycleLogger.addAppender(appender);

		try {
			ExtensionContext ctx = mock(ExtensionContext.class);
			when(ctx.getRequiredTestClass()).then(invocation -> sampleClass);

			new GraphQLMetricsExtension().afterAll(ctx);

			assertThat(appender.list)
					.filteredOn(event -> event.getLevel() == Level.ERROR)
					.extracting(ILoggingEvent::getFormattedMessage)
					.noneMatch(msg -> msg.contains("no test is running"));
		} finally {
			allureLifecycleLogger.detachAppender(appender);
		}
	}

	/** Marker class whose name attribution is used in the test above. */
	private static final class SampleAttributedTest {}
}
