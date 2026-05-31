package com.snowedunderproductions.graphprobe.codegen.gradle;

import com.snowedunderproductions.graphprobe.codegen.CodegenConfig;
import com.snowedunderproductions.graphprobe.codegen.CodegenYamlConfigLoader;
import com.snowedunderproductions.graphprobe.codegen.FixtureMapping;
import com.snowedunderproductions.graphprobe.codegen.GenerationResult;
import com.snowedunderproductions.graphprobe.codegen.GraphProbeCodegenEngine;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class GenerateGraphProbeTestsTask extends DefaultTask {

    @InputFiles
    public abstract ConfigurableFileCollection getSchemaFiles();

    @Input
    public abstract Property<String> getBasePackage();

    @Input
    @Optional
    public abstract Property<String> getDgsCodegenPackage();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    public abstract Property<Integer> getMaxGeneratedTestsPerOperation();

    @Input
    public abstract ListProperty<String> getOperationIncludePatterns();

    @Input
    public abstract ListProperty<String> getOperationExcludePatterns();

    @Input
    public abstract Property<String> getTestStyle();

    @InputFile
    @Optional
    public abstract RegularFileProperty getFixtureMappingsFile();

    @TaskAction
    public void generate() throws IOException {
        CodegenConfig config = new CodegenConfig();
        config.setSchemaFiles(getSchemaFiles().getFiles().stream().map(file -> file.toPath().toAbsolutePath()).toList());
        config.setBasePackage(getBasePackage().get());
        config.setDgsCodegenPackage(getDgsCodegenPackage().getOrNull());
        config.setOutputDirectory(getOutputDirectory().get().getAsFile().toPath().toAbsolutePath());
        config.setMaxGeneratedTestsPerOperation(getMaxGeneratedTestsPerOperation().get());
        config.setOperationIncludePatterns(getOperationIncludePatterns().getOrElse(List.of()));
        config.setOperationExcludePatterns(getOperationExcludePatterns().getOrElse(List.of()));
        config.setTestStyle(getTestStyle().getOrElse("all"));

        Map<String, FixtureMapping> fixtureMappings = new LinkedHashMap<>();
        if (getFixtureMappingsFile().isPresent()) {
            Path yamlPath = getFixtureMappingsFile().get().getAsFile().toPath().toAbsolutePath();
            CodegenConfig yamlConfig = new CodegenYamlConfigLoader().load(yamlPath);
            fixtureMappings.putAll(yamlConfig.getFixtureMappings());
            if (config.getSchemaFiles().isEmpty()) {
                config.setSchemaFiles(yamlConfig.getSchemaFiles());
            }
            if ((config.getBasePackage() == null || config.getBasePackage().isBlank()) && yamlConfig.getBasePackage() != null) {
                config.setBasePackage(yamlConfig.getBasePackage());
            }
        }

        Object extensionObject = getProject().getExtensions().findByName("graphProbeCodegen");
        if (extensionObject instanceof GraphProbeCodegenExtension extension) {
            for (Map.Entry<String, FixtureMappingSpec> entry : extension.getFixtureMappingsDsl().getMappings().entrySet()) {
                FixtureMapping mapping = new FixtureMapping();
                mapping.setSql(entry.getValue().getSql());
                mapping.setCsvResource(entry.getValue().getCsvResource());
                mapping.setArguments(new LinkedHashMap<>(entry.getValue().getArguments()));
                fixtureMappings.put(entry.getKey(), mapping);
            }
        }

        config.setFixtureMappings(fixtureMappings);

        GenerationResult result = new GraphProbeCodegenEngine().generate(config);
        getLogger().lifecycle("Generated {} file(s) into {}", result.getGeneratedFiles().size(), config.getOutputDirectory());
        for (Path file : result.getGeneratedFiles()) {
            getLogger().info(" - {}", file);
        }
    }
}
