package com.snowedunderproductions.graphprobe.allure.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Functional tests that exercise the full apply/registration/gating path in a real
 * Gradle build (where the Kotlin {@code io.qameta.allure} plugin and its DSL runtime
 * are present). Gated behind {@code -Dgraphprobe.functional=true} because they
 * resolve JUnit/Allure dependencies and the Allure commandline from the network.
 *
 * <p>Run with: {@code ./gradlew -p allure-gradle test -Dgraphprobe.functional=true}.
 */
@EnabledIfSystemProperty(named = "graphprobe.functional", matches = "true")
class GraphProbeAllureFunctionalTest {

    @TempDir
    Path projectDir;

    private void writeBuild(String testBody) throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle"), """
                pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
                rootProject.name = 'fixture'
                """);
        Files.writeString(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.snowedunderproductions.graphprobe.allure'
                }
                repositories { mavenCentral() }
                dependencies {
                    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.3'
                    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
                }
                test { useJUnitPlatform() }
                """);
        Path testDir = projectDir.resolve("src/test/java/fixture");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("SmokeTest.java"), """
                package fixture;
                import org.junit.jupiter.api.Test;
                class SmokeTest {
                    @Test void sample() { %s }
                }
                """.formatted(testBody));
    }

    private GradleRunner runner(String... args) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(args);
    }

    private long runDirCount() throws IOException {
        Path runs = projectDir.resolve("allure-archive/runs");
        if (!Files.isDirectory(runs)) {
            return 0;
        }
        try (var s = Files.list(runs)) {
            return s.filter(Files::isDirectory).count();
        }
    }

    @Test
    void accumulatesArchivesAndMergesReportWithTrend() throws Exception {
        writeBuild("");

        runner("testAccumulate").build();
        assertThat(runDirCount()).isEqualTo(1);

        runner("testAccumulate").build();
        assertThat(runDirCount()).isEqualTo(2);

        // Plain test must NOT add an archive (gating).
        runner("test").build();
        assertThat(runDirCount()).isEqualTo(2);

        runner("allureReportAll").build();
        assertThat(projectDir.resolve("build/reports/allure-report/allureReport/index.html")).exists();
        assertThat(Files.isDirectory(projectDir.resolve("allure-archive/history"))).isTrue();
    }

    @Test
    void archivesEvenWhenTestsFail() throws Exception {
        writeBuild("org.junit.jupiter.api.Assertions.fail();");

        BuildResult result = runner("testAccumulate").buildAndFail();

        assertThat(result.getOutput()).contains("Archived Allure run");
        assertThat(runDirCount()).isEqualTo(1);
    }
}
