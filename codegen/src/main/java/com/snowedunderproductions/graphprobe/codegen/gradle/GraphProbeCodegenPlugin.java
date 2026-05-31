package com.snowedunderproductions.graphprobe.codegen.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

public class GraphProbeCodegenPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        GraphProbeCodegenExtension extension = project.getExtensions().create(
            "graphProbeCodegen",
            GraphProbeCodegenExtension.class,
            project.getObjects()
        );

        extension.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/graphprobe-test/java"));
        extension.getMaxOperations().convention(200);
        extension.getTestStyle().convention("all");

        project.getTasks().register("generateGraphProbeTests", GenerateGraphProbeTestsTask.class, task -> {
            task.setDescription("Generate GraphProbe JUnit tests from GraphQL schema files");
            task.setGroup("verification");

            task.getSchemaFiles().from(extension.getSchemaFiles());
            task.getBasePackage().set(extension.getBasePackage());
            task.getDgsCodegenPackage().set(extension.getDgsCodegenPackage());
            task.getOutputDirectory().set(extension.getOutputDirectory());
            task.getPersistentOutputDirectory().set(extension.getPersistentOutputDirectory());
            task.getMaxOperations().set(extension.getMaxOperations());
            task.getOperationIncludePatterns().set(extension.getOperationIncludePatterns());
            task.getOperationExcludePatterns().set(extension.getOperationExcludePatterns());
            task.getTestStyle().set(extension.getTestStyle());
            task.getFixtureMappingsFile().set(extension.getFixtureMappingsFile());
            task.getFixtureMappingsFingerprint().set(project.provider(() ->
                GenerateGraphProbeTestsTask.fingerprint(extension.getFixtureMappingsDsl().getMappings())));
        });

        project.getPlugins().withType(JavaPlugin.class, ignored -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            sourceSets.named(SourceSet.TEST_SOURCE_SET_NAME, testSourceSet ->
                testSourceSet.getJava().srcDir(extension.getOutputDirectory())
            );
            project.getTasks().named(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME).configure(task ->
                task.dependsOn(project.getTasks().named("generateGraphProbeTests"))
            );
        });
    }
}
