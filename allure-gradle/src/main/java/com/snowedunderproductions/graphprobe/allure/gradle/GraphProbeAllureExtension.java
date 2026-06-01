package com.snowedunderproductions.graphprobe.allure.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/**
 * Configuration for the GraphProbe Allure result-accumulation plugin.
 *
 * <p>All properties carry sensible conventions set by
 * {@link GraphProbeAllurePlugin}, so most consumers never need a
 * {@code graphProbeAllure { }} block. Override any value to relocate the
 * durable archive or change the raw-results location.
 *
 * <p>The archive directory deliberately lives <em>outside</em> {@code build/}
 * so that {@code clean} and {@code cleanTest} never delete accumulated runs.
 */
public abstract class GraphProbeAllureExtension {

    /**
     * Durable archive root, outside {@code build/}. Holds {@code runs/<timestamp>/}
     * snapshots and the rolling {@code history/} used for trend continuity.
     * Convention: {@code <projectDir>/allure-archive}.
     */
    public abstract DirectoryProperty getArchiveDirectory();

    /**
     * The raw Allure results directory written by the test task. This is the
     * directory the {@code io.qameta.allure} adapter registers as a {@code test}
     * output. Convention: {@code <buildDir>/allure-results}.
     */
    public abstract DirectoryProperty getResultsDirectory();

    /**
     * {@link java.time.format.DateTimeFormatter} pattern used to name each
     * archived run folder. Convention: {@code yyyyMMdd-HHmmss}.
     */
    public abstract Property<String> getTimestampPattern();
}
