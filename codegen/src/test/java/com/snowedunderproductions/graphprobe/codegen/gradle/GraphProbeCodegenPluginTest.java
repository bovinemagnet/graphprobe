package com.snowedunderproductions.graphprobe.codegen.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphProbeCodegenPluginTest {

    @TempDir
    Path projectDir;

    @Test
    void pluginAddsGeneratedOutputToTestSources() {
        Project project = ProjectBuilder.builder()
            .withProjectDir(projectDir.toFile())
            .build();

        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply(GraphProbeCodegenPlugin.class);

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);

        assertThat(sourceSets.getByName("test").getJava().getSrcDirs())
            .contains(projectDir.resolve("build/generated/graphprobe-test/java").toFile());
        assertThat(project.getTasks().named("compileTestJava").get().getDependsOn())
            .contains(project.getTasks().named("generateGraphProbeTests"));
    }
}
