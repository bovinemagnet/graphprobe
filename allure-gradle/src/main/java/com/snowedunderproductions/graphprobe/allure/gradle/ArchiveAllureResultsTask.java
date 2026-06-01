package com.snowedunderproductions.graphprobe.allure.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Archives the current raw Allure results into a timestamped folder under the
 * durable archive ({@code allure-archive/runs/<timestamp>/}), excluding the
 * {@code history/} subtree (history is trend metadata, not a run's executions).
 *
 * <p>The timestamp is computed at <em>execution</em> time, so the destination
 * folder name is never captured at configuration time. Configuration-cache safe:
 * the action reads only its own properties and uses {@code java.nio}.
 */
public abstract class ArchiveAllureResultsTask extends DefaultTask {

    /** The raw results directory to snapshot. Optional: a no-op when absent/empty. */
    @InputDirectory
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getResultsDirectory();

    /** {@link DateTimeFormatter} pattern for the run folder name. */
    @Input
    public abstract Property<String> getTimestampPattern();

    /** Destination root: {@code allure-archive/runs}. */
    @Internal
    public abstract DirectoryProperty getRunsDirectory();

    @TaskAction
    public void archive() throws IOException {
        Path results = getResultsDirectory().get().getAsFile().toPath();
        if (!FileOps.isNonEmptyDirectory(results)) {
            getLogger().lifecycle("No Allure results to archive at {} — skipping.", results);
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(getTimestampPattern().get());
        String stamp = LocalDateTime.now().format(formatter);

        Path runsDir = getRunsDirectory().get().getAsFile().toPath();
        Path dest = runsDir.resolve(stamp);
        // Same-second collision guard: append a numeric suffix until unique.
        int suffix = 1;
        while (Files.exists(dest)) {
            dest = runsDir.resolve(stamp + "-" + suffix++);
        }

        FileOps.copyRecursively(results, dest, "history");
        getLogger().lifecycle("Archived Allure run to {}", dest);
    }
}
