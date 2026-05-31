package com.snowedunderproductions.graphprobe.codegen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CodegenConfig {
    private List<Path> schemaFiles = new ArrayList<>();
    private String basePackage;
    private String dgsCodegenPackage;
    private Path outputDirectory;
    private Path persistentOutputDirectory;
    private Path fixtureMappingsFile;
    private List<String> operationIncludePatterns = new ArrayList<>();
    private List<String> operationExcludePatterns = new ArrayList<>();
    private int maxGeneratedTestsPerOperation = 200;
    private String testStyle = "all";
    private Map<String, FixtureMapping> fixtureMappings = new LinkedHashMap<>();

    public List<Path> getSchemaFiles() {
        return schemaFiles;
    }

    public void setSchemaFiles(List<Path> schemaFiles) {
        this.schemaFiles = schemaFiles;
    }

    public String getBasePackage() {
        return basePackage;
    }

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

    public String getDgsCodegenPackage() {
        return dgsCodegenPackage;
    }

    public void setDgsCodegenPackage(String dgsCodegenPackage) {
        this.dgsCodegenPackage = dgsCodegenPackage;
    }

    public Path getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public Path getPersistentOutputDirectory() {
        return persistentOutputDirectory;
    }

    public void setPersistentOutputDirectory(Path persistentOutputDirectory) {
        this.persistentOutputDirectory = persistentOutputDirectory;
    }

    public Path getFixtureMappingsFile() {
        return fixtureMappingsFile;
    }

    public void setFixtureMappingsFile(Path fixtureMappingsFile) {
        this.fixtureMappingsFile = fixtureMappingsFile;
    }

    public List<String> getOperationIncludePatterns() {
        return operationIncludePatterns;
    }

    public void setOperationIncludePatterns(List<String> operationIncludePatterns) {
        this.operationIncludePatterns = operationIncludePatterns;
    }

    public List<String> getOperationExcludePatterns() {
        return operationExcludePatterns;
    }

    public void setOperationExcludePatterns(List<String> operationExcludePatterns) {
        this.operationExcludePatterns = operationExcludePatterns;
    }

    public int getMaxGeneratedTestsPerOperation() {
        return maxGeneratedTestsPerOperation;
    }

    public void setMaxGeneratedTestsPerOperation(int maxGeneratedTestsPerOperation) {
        this.maxGeneratedTestsPerOperation = maxGeneratedTestsPerOperation;
    }

    public String getTestStyle() {
        return testStyle;
    }

    public void setTestStyle(String testStyle) {
        this.testStyle = testStyle;
    }

    public Map<String, FixtureMapping> getFixtureMappings() {
        return fixtureMappings;
    }

    public void setFixtureMappings(Map<String, FixtureMapping> fixtureMappings) {
        this.fixtureMappings = fixtureMappings;
    }
}
