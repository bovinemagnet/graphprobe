package com.snowedunderproductions.graphprobe.codegen.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
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

    @Test
    void taskGeneratesSourcesFromConfiguredExtension() throws Exception {
        Path schema = projectDir.resolve("schema.graphqls");
        Files.writeString(schema, "type Query { ping: String }");

        GenerateGraphProbeTestsTask task = configuredTask(schema, "smoke", extension -> { });

        task.generate();

        assertThat(projectDir.resolve("out/com/example/generated/GeneratedSchemaSmokeTest.java")).exists();
    }

    @Test
    void taskAppliesDslFixtureMappings() throws Exception {
        Path schema = projectDir.resolve("schema.graphqls");
        Files.writeString(schema, """
            type Query { user(id: ID!): User }
            type User { id: ID! name: String }
            """);

        GenerateGraphProbeTestsTask task = configuredTask(schema, "fixture", extension ->
            extension.fixtureMappings(dsl -> dsl.operation("Query.user", spec -> {
                spec.setSql("SELECT id FROM users LIMIT 10");
                spec.setCsvResource("/graphprobe-fixtures/users.csv");
                spec.setLinesToSkip(1);
                spec.argument("id", "id");
            })));

        task.generate();

        Path provider = projectDir.resolve("out/com/example/generated/providers/QueryuserArgumentsProvider.java");
        Path fixtureTest = projectDir.resolve("out/com/example/generated/GeneratedFixtureBackedTest.java");
        assertThat(provider).exists();
        assertThat(Files.readString(provider)).contains("SELECT id FROM users LIMIT 10");
        assertThat(Files.readString(fixtureTest)).contains("csvResource = \"/graphprobe-fixtures/users.csv\"");
    }

    @Test
    void taskHonoursPersistentOutputDirectory() throws Exception {
        Path schema = projectDir.resolve("schema.graphqls");
        Files.writeString(schema, "type Query { ping: String }");
        Path persistent = projectDir.resolve("src/test/java");

        GenerateGraphProbeTestsTask task = configuredTask(schema, "smoke", extension ->
            extension.getPersistentOutputDirectory().set(persistent.toFile()));

        task.generate();

        assertThat(persistent.resolve("com/example/generated/GeneratedSchemaSmokeTest.java")).exists();
        assertThat(projectDir.resolve("out/com/example/generated/GeneratedSchemaSmokeTest.java")).doesNotExist();
    }

    @Test
    void fixtureMappingFingerprintChangesWithContent() {
        FixtureMappingsDsl original = new FixtureMappingsDsl();
        original.operation("Query.user", spec -> {
            spec.setSql("SELECT 1");
            spec.setCsvResource("/original.csv");
            spec.argument("id", "id");
        });
        FixtureMappingsDsl changed = new FixtureMappingsDsl();
        changed.operation("Query.user", spec -> {
            spec.setSql("SELECT 2");
            spec.setCsvResource("/changed.csv");
            spec.argument("id", "id");
        });

        assertThat(GenerateGraphProbeTestsTask.fingerprint(original.getMappings()))
            .isNotEqualTo(GenerateGraphProbeTestsTask.fingerprint(changed.getMappings()));
    }

    private GenerateGraphProbeTestsTask configuredTask(Path schema, String testStyle,
                                                       java.util.function.Consumer<GraphProbeCodegenExtension> customiser) {
        Project project = ProjectBuilder.builder()
            .withProjectDir(projectDir.toFile())
            .build();
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply(GraphProbeCodegenPlugin.class);

        GraphProbeCodegenExtension extension = project.getExtensions().getByType(GraphProbeCodegenExtension.class);
        extension.getSchemaFiles().from(schema);
        extension.getBasePackage().set("com.example.generated");
        extension.getTestStyle().set(testStyle);
        extension.getOutputDirectory().set(projectDir.resolve("out").toFile());
        customiser.accept(extension);

        return (GenerateGraphProbeTestsTask) project.getTasks().getByName("generateGraphProbeTests");
    }
}
