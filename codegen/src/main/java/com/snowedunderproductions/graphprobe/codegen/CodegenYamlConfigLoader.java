package com.snowedunderproductions.graphprobe.codegen;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CodegenYamlConfigLoader {
    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public CodegenConfig load(Path yamlPath) throws IOException {
        YamlConfig yamlConfig = objectMapper.readValue(yamlPath.toFile(), YamlConfig.class);
        CodegenConfig config = new CodegenConfig();
        Path baseDir = yamlPath.toAbsolutePath().getParent();
        if (baseDir == null) {
            baseDir = Path.of(".").toAbsolutePath();
        }
        List<Path> schemaPaths = new ArrayList<>();
        if (yamlConfig.schemaFiles != null) {
            for (String schemaFile : yamlConfig.schemaFiles) {
                schemaPaths.add(baseDir.resolve(schemaFile).normalize());
            }
        }
        config.setSchemaFiles(schemaPaths);
        config.setBasePackage(yamlConfig.basePackage);
        config.setDgsCodegenPackage(yamlConfig.dgsCodegenPackage);
        if (yamlConfig.outputDirectory != null && !yamlConfig.outputDirectory.isBlank()) {
            config.setOutputDirectory(baseDir.resolve(yamlConfig.outputDirectory).normalize());
        }
        if (yamlConfig.persistentOutputDirectory != null && !yamlConfig.persistentOutputDirectory.isBlank()) {
            config.setPersistentOutputDirectory(baseDir.resolve(yamlConfig.persistentOutputDirectory).normalize());
        }
        if (yamlConfig.operationIncludePatterns != null) {
            config.setOperationIncludePatterns(yamlConfig.operationIncludePatterns);
        }
        if (yamlConfig.operationExcludePatterns != null) {
            config.setOperationExcludePatterns(yamlConfig.operationExcludePatterns);
        }
        if (yamlConfig.maxOperations != null) {
            config.setMaxOperations(yamlConfig.maxOperations);
        }
        if (yamlConfig.testStyle != null) {
            config.setTestStyle(yamlConfig.testStyle);
        }
        if (yamlConfig.fixtureMappings != null) {
            config.setFixtureMappings(yamlConfig.fixtureMappings);
        }
        return config;
    }

    private static class YamlConfig {
        public List<String> schemaFiles = new ArrayList<>();
        public String basePackage;
        public String dgsCodegenPackage;
        // note: csvResource on FixtureMapping was removed; YAML keys for it are ignored.
        public String outputDirectory;
        public String persistentOutputDirectory;
        public Integer maxOperations;
        public String testStyle;
        public List<String> operationIncludePatterns = new ArrayList<>();
        public List<String> operationExcludePatterns = new ArrayList<>();
        public Map<String, FixtureMapping> fixtureMappings = new LinkedHashMap<>();
    }
}
