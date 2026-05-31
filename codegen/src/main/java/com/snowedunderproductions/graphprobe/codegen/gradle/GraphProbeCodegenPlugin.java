package com.snowedunderproductions.graphprobe.codegen.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class GraphProbeCodegenPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        GraphProbeCodegenExtension extension = project.getExtensions().create(
            "graphProbeCodegen",
            GraphProbeCodegenExtension.class,
            project.getObjects()
        );

        extension.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/graphprobe-test/java"));
        extension.getMaxGeneratedTestsPerOperation().convention(200);
        extension.getTestStyle().convention("all");

        project.getTasks().register("generateGraphProbeTests", GenerateGraphProbeTestsTask.class, task -> {
            task.setDescription("Generate GraphProbe JUnit tests from GraphQL schema files");
            task.setGroup("verification");

            task.getSchemaFiles().from(extension.getSchemaFiles());
            task.getBasePackage().set(extension.getBasePackage());
            task.getDgsCodegenPackage().set(extension.getDgsCodegenPackage());
            task.getOutputDirectory().set(extension.getOutputDirectory());
            task.getMaxGeneratedTestsPerOperation().set(extension.getMaxGeneratedTestsPerOperation());
            task.getOperationIncludePatterns().set(extension.getOperationIncludePatterns());
            task.getOperationExcludePatterns().set(extension.getOperationExcludePatterns());
            task.getTestStyle().set(extension.getTestStyle());
            task.getFixtureMappingsFile().set(extension.getFixtureMappingsFile());
        });
    }
}
