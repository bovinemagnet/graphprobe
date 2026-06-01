package com.snowedunderproductions.graphprobe.allure.gradle;

import io.qameta.allure.gradle.report.tasks.AllureReport;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Externalises Allure result accumulation as a reusable Gradle plugin.
 *
 * <p>Applying {@code com.snowedunderproductions.graphprobe.allure} also applies
 * {@code io.qameta.allure} (so {@code test} emits {@code build/allure-results})
 * and registers tasks that keep a durable archive of every run outside
 * {@code build/} and merge runs into one trend report:
 *
 * <ul>
 *   <li>{@code testAccumulate} — wipe raw results, run the suite fresh, archive the
 *       run to {@code allure-archive/runs/<timestamp>/} (even if tests fail).</li>
 *   <li>{@code allureReportAll} — one report merging all archived runs (or a
 *       {@code -Pruns=ts1,ts2} subset), with a trend across runs.</li>
 * </ul>
 *
 * <p>The "archive only when accumulating" gate is computed from the requested
 * task names at <em>configuration</em> time (never by querying the task graph at
 * execution time), keeping the plugin configuration-cache clean.
 */
public class GraphProbeAllurePlugin implements Plugin<Project> {

    static final String ALLURE_PLUGIN_ID = "io.qameta.allure";
    static final String ALLURE_REPORT_TASK = "allureReport";

    @Override
    public void apply(Project project) {
        // Single-plugin UX: bring in the Allure toolchain for the consumer.
        project.getPluginManager().apply(ALLURE_PLUGIN_ID);

        GraphProbeAllureExtension extension = project.getExtensions().create(
                "graphProbeAllure", GraphProbeAllureExtension.class);
        extension.getArchiveDirectory().convention(
                project.getLayout().getProjectDirectory().dir("allure-archive"));
        extension.getResultsDirectory().convention(
                project.getLayout().getBuildDirectory().dir("allure-results"));
        extension.getTimestampPattern().convention("yyyyMMdd-HHmmss");

        Provider<org.gradle.api.file.Directory> runsDir = extension.getArchiveDirectory().dir("runs");
        Provider<org.gradle.api.file.Directory> historyStore = extension.getArchiveDirectory().dir("history");
        Provider<org.gradle.api.file.Directory> reportHistoryDir =
                project.getLayout().getBuildDirectory().dir("reports/allure-report/allureReport/history");

        var cleanTask = project.getTasks().register("cleanAllureRawResults",
                CleanAllureRawResultsTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Deletes the raw Allure results so the next accumulating run is a clean snapshot.");
                    task.getResultsDirectory().set(extension.getResultsDirectory());
                });

        project.getTasks().register("archiveAllureResults", ArchiveAllureResultsTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Archives build/allure-results into allure-archive/runs/<timestamp>/.");
            task.getResultsDirectory().set(extension.getResultsDirectory());
            task.getTimestampPattern().set(extension.getTimestampPattern());
            task.getRunsDirectory().set(runsDir);
        });

        project.getTasks().register("seedAllureHistory", SeedAllureHistoryTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Seeds persisted Allure trend history into build/allure-results/history before a report.");
            task.getPersistedHistoryDirectory().set(historyStore);
            task.getResultsDirectory().set(extension.getResultsDirectory());
        });

        project.getTasks().register("persistAllureHistory", PersistAllureHistoryTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Persists generated Allure trend history to allure-archive/history after a report.");
            task.getReportHistoryDirectory().set(reportHistoryDir);
            task.getPersistedHistoryDirectory().set(historyStore);
        });

        project.getTasks().register("allureReportAll", task -> {
            task.setGroup("reporting");
            task.setDescription("Generates one Allure report merging archived runs (all, or -Pruns=ts1,ts2) with trend.");
            task.dependsOn("seedAllureHistory", ALLURE_REPORT_TASK);
            task.finalizedBy("persistAllureHistory");
        });

        project.getTasks().register("testAccumulate", task -> {
            task.setGroup("verification");
            task.setDescription("Runs the full test suite fresh and archives its Allure results outside build/.");
            task.dependsOn(cleanTask, "test");
            task.doLast(t -> t.getLogger().lifecycle(
                    "Accumulated Allure run; archive under {}", runsDir.get().getAsFile()));
        });

        // Ensure the raw-results wipe runs before the test it precedes in testAccumulate.
        // (No effect on a plain `test` run, where cleanAllureRawResults is not scheduled.)
        project.getPlugins().withType(JavaPlugin.class, ignored ->
                project.getTasks().named("test").configure(t -> t.mustRunAfter(cleanTask)));

        // ---- Configuration-time gating from the requested task names ----
        List<String> requested = project.getGradle().getStartParameter().getTaskNames();

        // Archive on success AND failure (finalizer), but ONLY when testAccumulate was
        // requested — so plain `gradle test` is never altered.
        if (shouldArchiveOnTest(requested)) {
            project.getPlugins().withType(JavaPlugin.class, ignored -> {
                project.getTasks().named("test").configure(t -> t.finalizedBy("archiveAllureResults"));
                project.getTasks().named("archiveAllureResults").configure(t -> t.mustRunAfter("test"));
            });
        }

        // Merge archived runs into the report ONLY when allureReportAll was requested,
        // so plain `gradle allureReport` keeps reporting just the latest run.
        if (isRequested(requested, "allureReportAll")) {
            ConfigurableFileCollection selectedRuns = selectedRunDirectories(project, runsDir);
            project.getPlugins().withId(ALLURE_PLUGIN_ID, ignored ->
                    project.getTasks().named(ALLURE_REPORT_TASK, AllureReport.class).configure(report -> {
                        report.dependsOn("seedAllureHistory");
                        report.getResultsDirs().from(selectedRuns);
                    }));
        }
    }

    /** A {@code -Pruns} subset, or all immediate child run directories when absent. */
    private static ConfigurableFileCollection selectedRunDirectories(
            Project project, Provider<org.gradle.api.file.Directory> runsDir) {
        Provider<String> runsProperty = project.getProviders().gradleProperty("runs");
        ConfigurableFileCollection files = project.getObjects().fileCollection();
        files.from(project.provider(() -> {
            File root = runsDir.get().getAsFile();
            File[] children = root.listFiles(File::isDirectory);
            if (children == null) {
                return List.of();
            }
            if (runsProperty.isPresent() && !runsProperty.get().isBlank()) {
                Set<String> wanted = Arrays.stream(runsProperty.get().split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
                return Arrays.stream(children).filter(d -> wanted.contains(d.getName())).toList();
            }
            return Arrays.asList(children);
        }));
        return files;
    }

    /** True when {@code testAccumulate} is among the requested task names. */
    static boolean shouldArchiveOnTest(List<String> requestedTaskNames) {
        return isRequested(requestedTaskNames, "testAccumulate");
    }

    private static boolean isRequested(List<String> requestedTaskNames, String taskName) {
        return requestedTaskNames.stream()
                .anyMatch(n -> n.equals(taskName) || n.endsWith(":" + taskName));
    }
}
