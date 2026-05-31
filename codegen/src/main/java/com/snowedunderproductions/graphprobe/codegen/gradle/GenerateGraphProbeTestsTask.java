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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @OutputDirectory
    @Optional
    public abstract DirectoryProperty getPersistentOutputDirectory();

    @Input
    public abstract Property<Integer> getMaxOperations();

    @Input
    public abstract ListProperty<String> getOperationIncludePatterns();

    @Input
    public abstract ListProperty<String> getOperationExcludePatterns();

    @Input
    public abstract Property<String> getTestStyle();

    @InputFile
    @Optional
    public abstract RegularFileProperty getFixtureMappingsFile();

    /**
     * A deterministic fingerprint of the DSL {@code fixtureMappings { }} block, set by the plugin.
     * The DSL specs themselves are not directly fingerprintable Gradle inputs, so this property lets
     * Gradle detect DSL changes and re-run generation; without it, edits to inline mappings would be
     * silently ignored by up-to-date checks.
     */
    @Input
    @Optional
    public abstract Property<String> getFixtureMappingsFingerprint();

    @TaskAction
    public void generate() throws IOException {
        CodegenConfig config = new CodegenConfig();
        config.setSchemaFiles(getSchemaFiles().getFiles().stream().map(file -> file.toPath().toAbsolutePath()).toList());
        config.setBasePackage(getBasePackage().get());
        config.setDgsCodegenPackage(getDgsCodegenPackage().getOrNull());
        config.setOutputDirectory(getOutputDirectory().get().getAsFile().toPath().toAbsolutePath());
        if (getPersistentOutputDirectory().isPresent()) {
            config.setPersistentOutputDirectory(getPersistentOutputDirectory().get().getAsFile().toPath().toAbsolutePath());
        }
        config.setMaxOperations(getMaxOperations().get());
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
            // DSL mappings overlay (and override) YAML mappings on a key collision.
            extension.getFixtureMappingsDsl().getMappings()
                .forEach((operation, spec) -> fixtureMappings.put(operation, toFixtureMapping(spec)));
        }

        config.setFixtureMappings(fixtureMappings);

        GenerationResult result = new GraphProbeCodegenEngine().generate(config);
        Path destination = config.getPersistentOutputDirectory() != null
            ? config.getPersistentOutputDirectory()
            : config.getOutputDirectory();
        getLogger().lifecycle("Generated {} file(s) into {}", result.getGeneratedFiles().size(), destination);
        for (Path file : result.getGeneratedFiles()) {
            getLogger().info(" - {}", file);
        }
        if (!result.getSkippedOperations().isEmpty()) {
            getLogger().warn("Skipped {} operation(s) beyond maxOperations ({}): {}",
                result.getSkippedOperations().size(), config.getMaxOperations(), result.getSkippedOperations());
        }
    }

    private static FixtureMapping toFixtureMapping(FixtureMappingSpec spec) {
        FixtureMapping mapping = new FixtureMapping();
        mapping.setSql(spec.getSql());
        mapping.setArguments(new LinkedHashMap<>(spec.getArguments()));
        return mapping;
    }

    /** Builds a stable, order-independent fingerprint of the DSL fixture mappings for change detection. */
    static String fingerprint(Map<String, FixtureMappingSpec> mappings) {
        return mappings.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                FixtureMappingSpec spec = entry.getValue();
                String arguments = spec.getArguments().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(arg -> arg.getKey() + "=" + arg.getValue())
                    .collect(Collectors.joining(","));
                return entry.getKey() + "|" + spec.getSql() + "|" + arguments;
            })
            .collect(Collectors.joining(";"));
    }
}
