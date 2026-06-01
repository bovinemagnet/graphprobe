package com.snowedunderproductions.graphprobe.allure.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Deletes the raw Allure results directory so the next accumulating run produces
 * an archive snapshot of exactly one run.
 *
 * <p>Configuration-cache safe: the action reads only its own
 * {@link DirectoryProperty} and uses {@code java.nio} — it never touches the
 * {@code Project} at execution time.
 */
public abstract class CleanAllureRawResultsTask extends DefaultTask {

    /** The raw results directory to delete (e.g. {@code build/allure-results}). */
    @Internal
    public abstract DirectoryProperty getResultsDirectory();

    @TaskAction
    public void clean() throws IOException {
        Path dir = getResultsDirectory().get().getAsFile().toPath();
        FileOps.deleteRecursively(dir);
        getLogger().info("Cleared raw Allure results at {}", dir);
    }
}
