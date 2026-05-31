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
    private Path outputDirectory = Path.of("build/generated/graphprobe-test/java");
    private Path persistentOutputDirectory;
    private Path fixtureMappingsFile;
    private List<String> operationIncludePatterns = new ArrayList<>();
    private List<String> operationExcludePatterns = new ArrayList<>();
    private List<String> operationTypes = new ArrayList<>(List.of("query"));
    private int maxOperations = 200;
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

    /**
     * Sets the base package for generated test sources, for example {@code com.example.generated}.
     * Must be a valid Java package name; this is enforced when {@link GraphProbeCodegenEngine#generate}
     * runs, which rejects a malformed value with a descriptive error.
     */
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

    public List<String> getOperationTypes() {
        return operationTypes;
    }

    /**
     * Sets the GraphQL operation types to generate. Supported values are {@code query},
     * {@code mutation}, {@code subscription}, and {@code all}. Query generation remains
     * the default; mutation and subscription tests are opt in.
     */
    public void setOperationTypes(List<String> operationTypes) {
        this.operationTypes = operationTypes;
    }

    public int getMaxOperations() {
        return maxOperations;
    }

    /**
     * Caps the number of query operations for which tests are generated (operations are sorted
     * by name and the first {@code maxOperations} are kept). Must be at least 1; the default is 200.
     * When the schema has more matching operations than this limit, the excess are skipped and
     * reported via {@link GenerationResult#getSkippedOperations()}.
     */
    public void setMaxOperations(int maxOperations) {
        this.maxOperations = maxOperations;
    }

    public String getTestStyle() {
        return testStyle;
    }

    /**
     * Sets the test style to generate. Must be one of {@code smoke}, {@code property},
     * {@code fixture}, or {@code all} (case-insensitive); {@code all} is the default.
     * An unrecognised value is rejected when {@link GraphProbeCodegenEngine#generate} runs.
     */
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
