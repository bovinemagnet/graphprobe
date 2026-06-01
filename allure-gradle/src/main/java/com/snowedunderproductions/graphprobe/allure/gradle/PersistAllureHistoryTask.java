package com.snowedunderproductions.graphprobe.allure.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Persists the trend history from a freshly generated Allure report
 * ({@code build/reports/allure-report/allureReport/history/}) back into the
 * durable store ({@code allure-archive/history/}) so the next report seeds from it.
 *
 * <p>Configuration-cache safe: reads only its own properties and uses
 * {@code java.nio}; no {@code Project} access at execution time.
 */
public abstract class PersistAllureHistoryTask extends DefaultTask {

    /** Generated report's history directory. */
    @Internal
    public abstract DirectoryProperty getReportHistoryDirectory();

    /** Durable persisted history store: {@code allure-archive/history}. */
    @Internal
    public abstract DirectoryProperty getPersistedHistoryDirectory();

    @TaskAction
    public void persist() throws IOException {
        Path reportHistory = getReportHistoryDirectory().get().getAsFile().toPath();
        if (!FileOps.isNonEmptyDirectory(reportHistory)) {
            getLogger().info("No report history at {} — nothing to persist.", reportHistory);
            return;
        }
        Path store = getPersistedHistoryDirectory().get().getAsFile().toPath();
        FileOps.deleteRecursively(store);
        FileOps.copyRecursively(reportHistory, store, null);
        getLogger().info("Persisted Allure history to {}", store);
    }
}
