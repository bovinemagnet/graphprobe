package com.snowedunderproductions.graphprobe.allure.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the plugin's gating logic and task actions.
 *
 * <p>These deliberately do NOT apply the plugin (which would apply the Kotlin
 * {@code io.qameta.allure} plugin and require the Gradle Kotlin DSL runtime).
 * Task actions are exercised by instantiating the task types directly. The full
 * apply/registration/gating path is covered by
 * {@link GraphProbeAllureFunctionalTest} in a real Gradle build.
 */
class GraphProbeAllurePluginTest {

    @TempDir
    Path projectDir;

    private Project project() {
        return ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
    }

    @Test
    void shouldArchiveOnTestGatesOnRequestedTaskNames() {
        assertThat(GraphProbeAllurePlugin.shouldArchiveOnTest(List.of())).isFalse();
        assertThat(GraphProbeAllurePlugin.shouldArchiveOnTest(List.of("test"))).isFalse();
        assertThat(GraphProbeAllurePlugin.shouldArchiveOnTest(List.of("clean", "test"))).isFalse();
        assertThat(GraphProbeAllurePlugin.shouldArchiveOnTest(List.of("allureReport"))).isFalse();
        assertThat(GraphProbeAllurePlugin.shouldArchiveOnTest(List.of("testAccumulate"))).isTrue();
        assertThat(GraphProbeAllurePlugin.shouldArchiveOnTest(List.of("clean", "testAccumulate"))).isTrue();
        assertThat(GraphProbeAllurePlugin.shouldArchiveOnTest(List.of(":sub:testAccumulate"))).isTrue();
    }

    @Test
    void archiveTaskSnapshotsResultsExcludingHistory() throws Exception {
        Path results = Files.createDirectories(projectDir.resolve("build/allure-results"));
        Files.writeString(results.resolve("a-result.json"), "{}");
        Files.writeString(results.resolve("b-container.json"), "{}");
        Files.createDirectories(results.resolve("history"));
        Files.writeString(results.resolve("history/history.json"), "{}");

        Path runs = projectDir.resolve("allure-archive/runs");

        ArchiveAllureResultsTask task = project().getTasks()
                .create("archiveAllureResults", ArchiveAllureResultsTask.class);
        task.getResultsDirectory().set(results.toFile());
        task.getRunsDirectory().set(runs.toFile());
        task.getTimestampPattern().set("yyyyMMdd-HHmmssSSS");
        task.archive();

        List<Path> runDirs = Files.list(runs).toList();
        assertThat(runDirs).hasSize(1);
        Path archived = runDirs.get(0);
        assertThat(archived.resolve("a-result.json")).exists();
        assertThat(archived.resolve("b-container.json")).exists();
        assertThat(archived.resolve("history")).doesNotExist();
    }

    @Test
    void archiveTaskIsNoOpWhenResultsEmpty() throws Exception {
        Path results = Files.createDirectories(projectDir.resolve("build/allure-results"));
        Path runs = projectDir.resolve("allure-archive/runs");

        ArchiveAllureResultsTask task = project().getTasks()
                .create("archiveAllureResults", ArchiveAllureResultsTask.class);
        task.getResultsDirectory().set(results.toFile());
        task.getRunsDirectory().set(runs.toFile());
        task.getTimestampPattern().set("yyyyMMdd-HHmmssSSS");
        task.archive();

        assertThat(Files.exists(runs)).isFalse();
    }

    @Test
    void seedAndPersistRoundTripHistory() throws Exception {
        Project project = project();
        Path store = Files.createDirectories(projectDir.resolve("allure-archive/history"));
        Files.writeString(store.resolve("history-trend.json"), "[]");
        Path results = Files.createDirectories(projectDir.resolve("build/allure-results"));

        SeedAllureHistoryTask seed = project.getTasks()
                .create("seedAllureHistory", SeedAllureHistoryTask.class);
        seed.getPersistedHistoryDirectory().set(store.toFile());
        seed.getResultsDirectory().set(results.toFile());
        seed.seed();
        assertThat(results.resolve("history/history-trend.json")).exists();

        // Simulate a generated report history, then persist it back to the store.
        Path reportHistory = Files.createDirectories(
                projectDir.resolve("build/reports/allure-report/allureReport/history"));
        Files.writeString(reportHistory.resolve("history-trend.json"), "[{\"data\":1}]");

        PersistAllureHistoryTask persist = project.getTasks()
                .create("persistAllureHistory", PersistAllureHistoryTask.class);
        persist.getReportHistoryDirectory().set(reportHistory.toFile());
        persist.getPersistedHistoryDirectory().set(store.toFile());
        persist.persist();
        assertThat(Files.readString(store.resolve("history-trend.json"))).contains("data");
    }

    @Test
    void cleanTaskDeletesRawResults() throws Exception {
        Path results = Files.createDirectories(projectDir.resolve("build/allure-results"));
        Files.writeString(results.resolve("a-result.json"), "{}");

        CleanAllureRawResultsTask task = project().getTasks()
                .create("cleanAllureRawResults", CleanAllureRawResultsTask.class);
        task.getResultsDirectory().set(results.toFile());
        task.clean();

        assertThat(Files.exists(results)).isFalse();
    }
}
