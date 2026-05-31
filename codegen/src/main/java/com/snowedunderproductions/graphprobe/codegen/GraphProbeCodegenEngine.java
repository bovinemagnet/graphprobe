package com.snowedunderproductions.graphprobe.codegen;

import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
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
    private static final Set<String> VALID_OPERATION_TYPES = Set.of("query", "mutation", "subscription", "all");

    private final SchemaLoader schemaLoader = new SchemaLoader();
    private final TestSourceGenerator sourceGenerator = new TestSourceGenerator(new GraphQlQueryBuilder());

    public GenerationResult generate(CodegenConfig config) throws IOException {
        validate(config);

        TypeDefinitionRegistry registry = schemaLoader.loadRegistry(config.getSchemaFiles());
        String schemaHash = schemaLoader.schemaHash(config.getSchemaFiles());
        List<Pattern> includes = compile(config.getOperationIncludePatterns());
        List<Pattern> excludes = compile(config.getOperationExcludePatterns());

        List<GeneratedOperation> matchingOperations = selectedOperationTypes(config).stream()
            .flatMap(operationType -> operationsForType(registry, operationType).stream())
            .sorted(Comparator.comparing(GeneratedOperation::qualifiedName))
            .filter(operation -> shouldInclude(operation.fieldName(), operation.qualifiedName(), includes, excludes))
            .toList();
        List<GeneratedOperation> operations = matchingOperations.stream()
            .limit(config.getMaxOperations())
            .toList();
        Map<String, GeneratedOperation> operationsByName = operations.stream()
            .collect(Collectors.toMap(GeneratedOperation::fieldName, Function.identity(), (a, b) -> a));
        Map<String, GeneratedOperation> operationsByQualifiedName = operations.stream()
            .collect(Collectors.toMap(GeneratedOperation::qualifiedName, Function.identity()));

        Path baseOutputDir = config.getPersistentOutputDirectory() != null
            ? config.getPersistentOutputDirectory()
            : config.getOutputDirectory();
        Path packageDir = packageDirectory(baseOutputDir, config.getBasePackage());
        Files.createDirectories(packageDir);

        Map<String, String> sources = sourceGenerator.generate(config, registry, operations, operationsByName, operationsByQualifiedName, schemaHash);

        GenerationResult result = new GenerationResult();
        matchingOperations.stream()
            .skip(operations.size())
            .map(GeneratedOperation::qualifiedName)
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
        for (String operationType : selectedOperationTypes(config)) {
            if (!VALID_OPERATION_TYPES.contains(operationType)) {
                throw new IllegalArgumentException("operationTypes must contain only " + VALID_OPERATION_TYPES + " but included: " + operationType);
            }
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

    private List<String> selectedOperationTypes(CodegenConfig config) {
        List<String> configured = config.getOperationTypes();
        if (configured == null || configured.isEmpty()) {
            configured = List.of("query");
        }
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String type : configured) {
            String normalized = type == null ? "" : type.trim().toLowerCase();
            if ("all".equals(normalized)) {
                selected.addAll(List.of("query", "mutation", "subscription"));
            } else if (!normalized.isBlank()) {
                selected.add(normalized);
            }
        }
        return selected.stream().toList();
    }

    private List<GeneratedOperation> operationsForType(TypeDefinitionRegistry registry, String operationType) {
        return schemaLoader.resolveOperationRoot(registry, operationType)
            .flatMap(rootType -> registry.getType(rootType, ObjectTypeDefinition.class)
                .map(typeDefinition -> typeDefinition.getFieldDefinitions().stream()
                    .map(field -> new GeneratedOperation(operationType, rootType, field))
                    .toList()))
            .orElseGet(List::of);
    }

    private boolean shouldInclude(String operation, String qualifiedOperation, List<Pattern> includes, List<Pattern> excludes) {
        boolean included = includes.isEmpty() || includes.stream().anyMatch(p ->
            p.matcher(operation).matches() || p.matcher(qualifiedOperation).matches());
        boolean excluded = excludes.stream().anyMatch(p ->
            p.matcher(operation).matches() || p.matcher(qualifiedOperation).matches());
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
