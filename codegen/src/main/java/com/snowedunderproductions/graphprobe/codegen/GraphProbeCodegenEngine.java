package com.snowedunderproductions.graphprobe.codegen;

import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Orchestrates schema-driven test generation: validates configuration, loads the GraphQL
 * schema, selects the operations to cover, then delegates source generation to
 * {@link TestSourceGenerator} and writes the results to disk.
 */
public class GraphProbeCodegenEngine {

    private static final Set<String> VALID_TEST_STYLES = Set.of("all", "smoke", "property", "fixture");

    private final SchemaLoader schemaLoader = new SchemaLoader();
    private final TestSourceGenerator sourceGenerator = new TestSourceGenerator(new GraphQlQueryBuilder());

    public GenerationResult generate(CodegenConfig config) throws IOException {
        validate(config);

        TypeDefinitionRegistry registry = schemaLoader.loadRegistry(config.getSchemaFiles());
        String schemaHash = schemaLoader.schemaHash(config.getSchemaFiles());
        String queryRoot = schemaLoader.resolveQueryRoot(registry);
        ObjectTypeDefinition queryType = registry.getType(queryRoot, ObjectTypeDefinition.class)
            .orElseThrow(() -> new IllegalArgumentException("Query root type '" + queryRoot + "' was not found in schema"));

        List<Pattern> includes = compile(config.getOperationIncludePatterns());
        List<Pattern> excludes = compile(config.getOperationExcludePatterns());

        List<FieldDefinition> matchingFields = queryType.getFieldDefinitions().stream()
            .sorted(Comparator.comparing(FieldDefinition::getName))
            .filter(field -> shouldInclude(field.getName(), includes, excludes))
            .toList();
        List<FieldDefinition> queryFields = matchingFields.stream()
            .limit(config.getMaxOperations())
            .toList();
        Map<String, FieldDefinition> queryFieldsByName = queryFields.stream()
            .collect(Collectors.toMap(FieldDefinition::getName, Function.identity()));

        Path baseOutputDir = config.getPersistentOutputDirectory() != null
            ? config.getPersistentOutputDirectory()
            : config.getOutputDirectory();
        Path packageDir = packageDirectory(baseOutputDir, config.getBasePackage());
        Files.createDirectories(packageDir);

        Map<String, String> sources = sourceGenerator.generate(config, registry, queryFields, queryFieldsByName, schemaHash);

        GenerationResult result = new GenerationResult();
        matchingFields.stream()
            .skip(queryFields.size())
            .map(FieldDefinition::getName)
            .forEach(result.getSkippedOperations()::add);
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path target = packageDir.resolve(source.getKey());
            write(target, source.getValue());
            result.getGeneratedFiles().add(target);
        }
        return result;
    }

    private void validate(CodegenConfig config) {
        if (config.getSchemaFiles() == null || config.getSchemaFiles().isEmpty()) {
            throw new IllegalArgumentException("schemaFiles must contain at least one schema file");
        }
        for (Path schema : config.getSchemaFiles()) {
            if (!Files.exists(schema)) {
                throw new IllegalArgumentException("Schema file does not exist: " + schema);
            }
        }
        if (config.getBasePackage() == null || config.getBasePackage().isBlank()) {
            throw new IllegalArgumentException("basePackage is required");
        }
        if (!config.getBasePackage().matches("[a-zA-Z_$][a-zA-Z0-9_$]*(\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*")) {
            throw new IllegalArgumentException("basePackage is not a valid Java package name: " + config.getBasePackage());
        }
        if (config.getTestStyle() != null && !VALID_TEST_STYLES.contains(config.getTestStyle().toLowerCase())) {
            throw new IllegalArgumentException("testStyle must be one of " + VALID_TEST_STYLES + " but was: " + config.getTestStyle());
        }
        if (config.getOutputDirectory() == null && config.getPersistentOutputDirectory() == null) {
            throw new IllegalArgumentException("outputDirectory or persistentOutputDirectory must be provided");
        }
        if (config.getMaxOperations() < 1) {
            throw new IllegalArgumentException("maxOperations must be >= 1");
        }
    }

    private List<Pattern> compile(List<String> patterns) {
        if (patterns == null) {
            return Collections.emptyList();
        }
        return patterns.stream().filter(s -> !s.isBlank()).map(Pattern::compile).toList();
    }

    private boolean shouldInclude(String operation, List<Pattern> includes, List<Pattern> excludes) {
        boolean included = includes.isEmpty() || includes.stream().anyMatch(p -> p.matcher(operation).matches());
        boolean excluded = excludes.stream().anyMatch(p -> p.matcher(operation).matches());
        return included && !excluded;
    }

    private Path packageDirectory(Path outputDir, String basePackage) {
        Path current = outputDir;
        for (String segment : basePackage.split("\\.")) {
            current = current.resolve(segment);
        }
        return current;
    }

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
