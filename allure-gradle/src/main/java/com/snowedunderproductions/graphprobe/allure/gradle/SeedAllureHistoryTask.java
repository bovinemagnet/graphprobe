package com.snowedunderproductions.graphprobe.allure.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Seeds the persisted Allure trend history ({@code allure-archive/history/}) into
 * the raw results directory ({@code build/allure-results/history/}) before a
 * report is generated, so the Trend graph carries forward across runs.
 *
 * <p>Configuration-cache safe: reads only its own properties and uses
 * {@code java.nio}; no {@code Project} access at execution time.
 */
public abstract class SeedAllureHistoryTask extends DefaultTask {

    /** Persisted history store: {@code allure-archive/history}. */
    @Internal
    public abstract DirectoryProperty getPersistedHistoryDirectory();

    /** Raw results directory whose {@code history/} subfolder is seeded. */
    @Internal
    public abstract DirectoryProperty getResultsDirectory();

    @TaskAction
    public void seed() throws IOException {
        Path store = getPersistedHistoryDirectory().get().getAsFile().toPath();
        if (!FileOps.isNonEmptyDirectory(store)) {
            getLogger().info("No persisted Allure history at {} — nothing to seed.", store);
            return;
        }
        Path target = getResultsDirectory().get().getAsFile().toPath().resolve("history");
        FileOps.copyRecursively(store, target, null);
        getLogger().info("Seeded Allure history into {}", target);
    }
}
