package com.snowedunderproductions.graphprobe.codegen;

import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GraphProbeCodegenEngine {
    private static final Set<String> BUILTIN_SCALARS = Set.of("String", "Int", "Float", "Boolean", "ID");

    public GenerationResult generate(CodegenConfig config) throws IOException {
        validate(config);

        TypeDefinitionRegistry registry = loadRegistry(config.getSchemaFiles());
        String schemaHash = schemaHash(config.getSchemaFiles());
        String queryRoot = resolveQueryRoot(registry);
        ObjectTypeDefinition queryType = registry.getType(queryRoot, ObjectTypeDefinition.class)
            .orElseThrow(() -> new IllegalArgumentException("Query root type '" + queryRoot + "' was not found in schema"));

        List<Pattern> includes = compile(config.getOperationIncludePatterns());
        List<Pattern> excludes = compile(config.getOperationExcludePatterns());

        List<FieldDefinition> queryFields = queryType.getFieldDefinitions().stream()
            .sorted(Comparator.comparing(FieldDefinition::getName))
            .filter(field -> shouldInclude(field.getName(), includes, excludes))
            .limit(config.getMaxGeneratedTestsPerOperation())
            .toList();
        Map<String, FieldDefinition> queryFieldsByName = queryFields.stream()
            .collect(Collectors.toMap(FieldDefinition::getName, Function.identity()));

        Path baseOutputDir = config.getPersistentOutputDirectory() != null
            ? config.getPersistentOutputDirectory()
            : config.getOutputDirectory();

        Path packageDir = packageDirectory(baseOutputDir, config.getBasePackage());
        Files.createDirectories(packageDir);

        GenerationResult result = new GenerationResult();

        Path smoke = packageDir.resolve("GeneratedSchemaSmokeTest.java");
        write(smoke, generateSmokeTest(config.getBasePackage(), schemaHash, queryFields, registry, config.getDgsCodegenPackage()));
        result.getGeneratedFiles().add(smoke);

        if (styleEnabled(config.getTestStyle(), "property")) {
            Path property = packageDir.resolve("GeneratedPropertyTest.java");
            write(property, generatePropertyTest(config.getBasePackage(), schemaHash, queryFields, registry));
            result.getGeneratedFiles().add(property);
        }

        if (styleEnabled(config.getTestStyle(), "fixture") && !config.getFixtureMappings().isEmpty()) {
            Path providersDir = packageDir.resolve("providers");
            Files.createDirectories(providersDir);
            List<FixtureTestBinding> fixtureBindings = new ArrayList<>();
            for (Map.Entry<String, FixtureMapping> entry : config.getFixtureMappings().entrySet()) {
                String operation = entry.getKey();
                FixtureMapping mapping = entry.getValue();
                String providerName = providerName(operation);
                FieldDefinition field = resolveOperationField(operation, queryFieldsByName);
                Path providerPath = providersDir.resolve(providerName + ".java");
                write(providerPath, generateProvider(config.getBasePackage(), schemaHash, providerName, operation, mapping));
                result.getGeneratedFiles().add(providerPath);
                fixtureBindings.add(new FixtureTestBinding(operation, providerName, new ArrayList<>(mapping.getArguments().keySet()), field));
            }
            Path fixture = packageDir.resolve("GeneratedFixtureBackedTest.java");
            write(fixture, generateFixtureTest(config.getBasePackage(), schemaHash, fixtureBindings, registry));
            result.getGeneratedFiles().add(fixture);
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
        if (config.getOutputDirectory() == null && config.getPersistentOutputDirectory() == null) {
            throw new IllegalArgumentException("outputDirectory or persistentOutputDirectory must be provided");
        }
        if (config.getMaxGeneratedTestsPerOperation() < 1) {
            throw new IllegalArgumentException("maxGeneratedTestsPerOperation must be >= 1");
        }
    }

    private TypeDefinitionRegistry loadRegistry(List<Path> schemaFiles) throws IOException {
        SchemaParser parser = new SchemaParser();
        TypeDefinitionRegistry combined = new TypeDefinitionRegistry();
        for (Path schemaFile : schemaFiles.stream().sorted().toList()) {
            combined.merge(parser.parse(Files.readString(schemaFile)));
        }
        return combined;
    }

    private String resolveQueryRoot(TypeDefinitionRegistry registry) {
        Optional<SchemaDefinition> schemaDefinition = registry.schemaDefinition();
        if (schemaDefinition.isPresent()) {
            for (OperationTypeDefinition operationTypeDefinition : schemaDefinition.get().getOperationTypeDefinitions()) {
                if ("query".equals(operationTypeDefinition.getName())) {
                    return operationTypeDefinition.getTypeName().getName();
                }
            }
        }
        return "Query";
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

    private String generateSmokeTest(String basePackage, String schemaHash, List<FieldDefinition> operations, TypeDefinitionRegistry registry, String dgsCodegenPackage) {
        String methods = operations.stream()
            .map(op -> smokeMethod(op, registry, dgsCodegenPackage))
            .collect(Collectors.joining("\n\n"));

        return "package " + basePackage + ";\n\n"
            + header(schemaHash)
            + "import static org.assertj.core.api.Assertions.assertThat;\n\n"
            + "import com.fasterxml.jackson.databind.JsonNode;\n"
            + "import com.fasterxml.jackson.databind.ObjectMapper;\n"
            + "import com.snowedunderproductions.graphprobe.client.SimpleGraphQLClient;\n"
            + "import org.junit.jupiter.api.Test;\n\n"
            + "class GeneratedSchemaSmokeTest {\n"
            + "    private static final SimpleGraphQLClient CLIENT = new SimpleGraphQLClient();\n"
            + "    private static final ObjectMapper MAPPER = new ObjectMapper();\n\n"
            + methods + "\n"
            + dgsHelper(dgsCodegenPackage)
            + "}\n";
    }

    private String smokeMethod(FieldDefinition operation, TypeDefinitionRegistry registry, String dgsCodegenPackage) {
        String methodName = "smoke_" + safeName(operation.getName());
        String query = operationQuery(operation, registry);
        String responseFieldExpression = dgsCodegenPackage == null || dgsCodegenPackage.isBlank()
            ? "\"" + javaString(operation.getName()) + "\""
            : "dgsQueryField(\"" + dgsConstantName(operation.getName()) + "\", \"" + javaString(operation.getName()) + "\")";
        return "    @Test\n"
            + "    void " + methodName + "() throws Exception {\n"
            + "        String query = \"\"\"\n" + indent(query, 8) + "\n        \"\"\";\n"
            + "        String response = CLIENT.executeFullQuery(query);\n"
            + "        JsonNode root = MAPPER.readTree(response);\n"
            + "        assertThat(root.path(\"errors\").isMissingNode() || root.path(\"errors\").isNull() || root.path(\"errors\").isEmpty()).isTrue();\n"
            + "        assertThat(root.path(\"data\").path(" + responseFieldExpression + ").isMissingNode()).isFalse();\n"
            + "    }";
    }

    private String generatePropertyTest(String basePackage, String schemaHash, List<FieldDefinition> operations, TypeDefinitionRegistry registry) {
        PropertyBinding binding = propertyBinding(operations, registry);
        String body;
        if (binding == null) {
            body = "    @GraphQLProperty(tries = 10)\n"
                + "    void generatedStringArbitraryIsUsable(@ForAll(\"generatedString\") String value) {\n"
                + "        assertThat(value).isNotNull();\n"
                + "    }\n\n"
                + "    @Provide\n"
                + "    Arbitrary<String> generatedString() {\n"
                + "        return GraphQLArbitraries.graphqlString();\n"
                + "    }";
        } else {
            String variableName = safeName(binding.argument().getName());
            String providerName = "generated" + capitalize(variableName);
            String queryExpression = javaQueryExpression(
                binding.operation(),
                registry,
                Map.of(binding.argument().getName(), typedLiteralExpression(binding.argument().getType(), registry, variableName))
            );
            String arbitraryType = arbitraryJavaType(binding.argument().getType(), registry);
            body = "    @GraphQLProperty(tries = 20)\n"
                + "    void property_" + safeName(binding.operation().getName()) + "(@ForAll(\"" + providerName + "\") " + arbitraryType + " " + variableName + ") throws Exception {\n"
                + "        String query = " + queryExpression + ";\n"
                + "        String response = CLIENT.executeFullQuery(query);\n"
                + "        assertThat(response).isNotBlank();\n"
                + "    }\n\n"
                + propertyProvider(providerName, binding.argument(), registry);
        }

        return "package " + basePackage + ";\n\n"
            + header(schemaHash)
            + "import static org.assertj.core.api.Assertions.assertThat;\n\n"
            + "import com.snowedunderproductions.graphprobe.client.SimpleGraphQLClient;\n"
            + "import com.snowedunderproductions.graphprobe.jqwik.annotations.GraphQLProperty;\n"
            + "import com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLArbitraries;\n"
            + "import com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLFuzzingArbitraries;\n"
            + "import net.jqwik.api.Arbitrary;\n"
            + "import net.jqwik.api.Arbitraries;\n"
            + "import net.jqwik.api.ForAll;\n"
            + "import net.jqwik.api.Provide;\n\n"
            + "class GeneratedPropertyTest {\n"
            + "    private static final SimpleGraphQLClient CLIENT = new SimpleGraphQLClient();\n\n"
            + body + "\n\n"
            + graphQlLiteralHelpers()
            + "}\n";
    }

    private String generateProvider(String basePackage, String schemaHash, String providerName, String operation, FixtureMapping mapping) {
        List<String> args = new ArrayList<>();
        if (mapping.getArguments() != null && !mapping.getArguments().isEmpty()) {
            mapping.getArguments().forEach((argument, column) -> args.add("            getSafeString(rs, \"" + column + "\")"));
        } else {
            args.add("            getSafeString(rs, rs.getMetaData().getColumnName(1))");
        }

        String sql = mapping.getSql() == null ? "SELECT 1" : mapping.getSql();

        return "package " + basePackage + ".providers;\n\n"
            + header(schemaHash)
            + "import com.snowedunderproductions.graphprobe.database.BaseArgumentsProvider;\n"
            + "import java.sql.ResultSet;\n"
            + "import java.sql.SQLException;\n"
            + "import org.junit.jupiter.params.provider.Arguments;\n\n"
            + "public class " + providerName + " extends BaseArgumentsProvider {\n"
            + "    @Override\n"
            + "    protected String getSQL() {\n"
            + "        return \"" + javaString(sql) + "\";\n"
            + "    }\n\n"
            + "    @Override\n"
            + "    protected Arguments extractArguments(ResultSet rs) throws SQLException {\n"
            + "        return Arguments.of(\n"
            + String.join(",\n", args)
            + "\n        );\n"
            + "    }\n\n"
            + "    @Override\n"
            + "    protected String getCacheKey() {\n"
            + "        return \"" + javaString(operation) + "\";\n"
            + "    }\n"
            + "}\n";
    }

    private String generateFixtureTest(String basePackage, String schemaHash, List<FixtureTestBinding> bindings, TypeDefinitionRegistry registry) {
        String methods = bindings.stream().map(binding -> {
            String args = binding.arguments.isEmpty()
                ? "String value"
                : binding.arguments.stream().map(arg -> "String " + safeName(arg)).collect(Collectors.joining(", "));

            String opField = binding.operation.contains(".") ? binding.operation.substring(binding.operation.indexOf('.') + 1) : binding.operation;
            Map<String, String> dynamicArguments = binding.arguments.stream()
                .collect(Collectors.toMap(Function.identity(), arg -> fixtureLiteralExpression(argumentType(binding.field, arg), registry, safeName(arg))));
            String queryExpression = javaQueryExpression(binding.field, registry, dynamicArguments);

            return "    @ParameterizedTest\n"
                + "    @DynamicSource(argumentsProvider = " + binding.providerName + ".class)\n"
                + "    void fixture_" + safeName(opField) + "(" + args + ") throws Exception {\n"
                + "        String query = " + queryExpression + ";\n"
                + "        String response = CLIENT.executeFullQuery(query);\n"
                + "        assertThat(response).isNotBlank();\n"
                + "    }";
        }).collect(Collectors.joining("\n\n"));
        String providerImports = bindings.stream()
            .map(binding -> "import " + basePackage + ".providers." + binding.providerName + ";\n")
            .distinct()
            .collect(Collectors.joining());

        return "package " + basePackage + ";\n\n"
            + header(schemaHash)
            + "import static org.assertj.core.api.Assertions.assertThat;\n\n"
            + "import com.snowedunderproductions.graphprobe.annotations.DynamicSource;\n"
            + "import com.snowedunderproductions.graphprobe.client.SimpleGraphQLClient;\n"
            + providerImports
            + "import org.junit.jupiter.params.ParameterizedTest;\n\n"
            + "class GeneratedFixtureBackedTest {\n"
            + "    private static final SimpleGraphQLClient CLIENT = new SimpleGraphQLClient();\n\n"
            + methods + "\n"
            + graphQlLiteralHelpers()
            + "}\n";
    }

    private String operationQuery(FieldDefinition operation, TypeDefinitionRegistry registry) {
        String args = argumentLiteral(operation.getInputValueDefinitions(), registry);
        String selection = selectionSet(operation.getType(), registry, 0, new LinkedHashSet<>());
        String field = operation.getName() + args + (selection.isBlank() ? "" : " " + selection);
        return "query Generated_" + operation.getName() + " {\n  " + field + "\n}";
    }

    private String argumentLiteral(List<InputValueDefinition> args, TypeDefinitionRegistry registry) {
        if (args == null || args.isEmpty()) {
            return "";
        }
        return "(" + args.stream().map(arg -> arg.getName() + ": " + sampleLiteral(arg.getType(), registry)).collect(Collectors.joining(", ")) + ")";
    }

    private String sampleLiteral(Type<?> type, TypeDefinitionRegistry registry) {
        if (type instanceof NonNullType nonNullType) {
            return sampleLiteral(nonNullType.getType(), registry);
        }
        if (type instanceof ListType listType) {
            return "[" + sampleLiteral(listType.getType(), registry) + "]";
        }
        TypeName typeName = (TypeName) type;
        String name = typeName.getName();
        return switch (name) {
            case "ID" -> "\"1\"";
            case "String" -> "\"sample\"";
            case "Int" -> "1";
            case "Float" -> "1.0";
            case "Boolean" -> "true";
            case "UUID" -> "\"00000000-0000-0000-0000-000000000000\"";
            case "Date" -> "\"2024-01-01\"";
            case "DateTime" -> "\"2024-01-01T00:00:00Z\"";
            case "BigDecimal" -> "\"1.00\"";
            case "URL" -> "\"https://example.com\"";
            case "Email" -> "\"test@example.com\"";
            default -> {
                Optional<EnumTypeDefinition> enumType = registry.getType(name, EnumTypeDefinition.class);
                if (enumType.isPresent() && !enumType.get().getEnumValueDefinitions().isEmpty()) {
                    yield enumType.get().getEnumValueDefinitions().get(0).getName();
                }
                yield "\"sample\"";
            }
        };
    }

    private String selectionSet(Type<?> returnType, TypeDefinitionRegistry registry, int depth, Set<String> visited) {
        TypeName typeName = unwrap(returnType);
        if (typeName == null) {
            return "";
        }
        String name = typeName.getName();
        if (BUILTIN_SCALARS.contains(name)) {
            return "";
        }
        if (registry.getType(name, EnumTypeDefinition.class).isPresent()) {
            return "";
        }
        if (depth >= 2 || visited.contains(name)) {
            return "{ __typename }";
        }
        Optional<ObjectTypeDefinition> objectType = registry.getType(name, ObjectTypeDefinition.class);
        if (objectType.isEmpty()) {
            return "";
        }

        visited.add(name);
        List<FieldDefinition> ordered = orderFields(objectType.get().getFieldDefinitions());
        List<String> fields = new ArrayList<>();
        for (FieldDefinition field : ordered) {
            TypeName fieldTypeName = unwrap(field.getType());
            if (fieldTypeName == null) {
                continue;
            }
            String childType = fieldTypeName.getName();
            if (BUILTIN_SCALARS.contains(childType) || registry.getType(childType, EnumTypeDefinition.class).isPresent()) {
                fields.add(field.getName());
            } else if (depth < 1) {
                String child = selectionSet(field.getType(), registry, depth + 1, visited);
                if (!child.isBlank()) {
                    fields.add(field.getName() + " " + child);
                }
            }
            if (fields.size() >= 5) {
                break;
            }
        }
        visited.remove(name);

        if (fields.isEmpty()) {
            return "{ __typename }";
        }

        return "{ " + String.join(" ", fields) + " }";
    }

    private List<FieldDefinition> orderFields(List<FieldDefinition> fields) {
        List<String> preferred = List.of("id", "name", "status", "title", "code");
        return fields.stream()
            .sorted(Comparator.comparingInt((FieldDefinition field) -> {
                int idx = preferred.indexOf(field.getName());
                return idx >= 0 ? idx : preferred.size() + 1;
            }).thenComparing(FieldDefinition::getName))
            .toList();
    }

    private TypeName unwrap(Type<?> type) {
        if (type instanceof NonNullType nonNullType) {
            return unwrap(nonNullType.getType());
        }
        if (type instanceof ListType listType) {
            return unwrap(listType.getType());
        }
        if (type instanceof TypeName typeName) {
            return typeName;
        }
        return null;
    }

    private String header(String schemaHash) {
        return "/*\n"
            + " * Generated by GraphProbe codegen\n"
            + " * Generator version: 1\n"
            + " * Source schema hash: " + schemaHash + "\n"
            + " */\n\n";
    }

    private String schemaHash(List<Path> schemaFiles) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
        for (Path file : schemaFiles.stream().sorted().toList()) {
            digest.update(Files.readAllBytes(file));
        }
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.substring(0, 12);
    }

    private boolean styleEnabled(String style, String requested) {
        String resolved = style == null ? "all" : style.toLowerCase();
        return "all".equals(resolved) || requested.equals(resolved);
    }

    private String indent(String input, int spaces) {
        String prefix = " ".repeat(spaces);
        return input.lines().map(line -> prefix + line).collect(Collectors.joining("\n"));
    }

    private String safeName(String input) {
        String cleaned = input.replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.isEmpty()) {
            return "value";
        }
        if (Character.isDigit(cleaned.charAt(0))) {
            return "_" + cleaned;
        }
        return cleaned;
    }

    private String providerName(String operation) {
        return operation.replaceAll("[^A-Za-z0-9]", "") + "ArgumentsProvider";
    }

    private String dgsHelper(String dgsCodegenPackage) {
        if (dgsCodegenPackage == null || dgsCodegenPackage.isBlank()) {
            return "";
        }
        return "\n"
            + "    private static String dgsQueryField(String constantName, String fallback) {\n"
            + "        try {\n"
            + "            Class<?> queryConstants = Class.forName(\"" + javaString(dgsCodegenPackage) + ".DgsConstants$QUERY\");\n"
            + "            Object value = queryConstants.getField(constantName).get(null);\n"
            + "            return value instanceof String stringValue ? stringValue : fallback;\n"
            + "        } catch (ReflectiveOperationException | LinkageError ignored) {\n"
            + "            return fallback;\n"
            + "        }\n"
            + "    }\n";
    }

    private String dgsConstantName(String fieldName) {
        StringBuilder result = new StringBuilder();
        boolean upperNext = true;
        for (char c : fieldName.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                upperNext = true;
                continue;
            }
            if (upperNext) {
                result.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                result.append(c);
            }
        }
        return result.isEmpty() ? "Value" : result.toString();
    }

    private FieldDefinition resolveOperationField(String operation, Map<String, FieldDefinition> queryFieldsByName) {
        String fieldName = operation.contains(".") ? operation.substring(operation.indexOf('.') + 1) : operation;
        FieldDefinition field = queryFieldsByName.get(fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Fixture mapping references unknown or excluded query operation: " + operation);
        }
        return field;
    }

    private Type<?> argumentType(FieldDefinition field, String argumentName) {
        return field.getInputValueDefinitions().stream()
            .filter(argument -> argument.getName().equals(argumentName))
            .findFirst()
            .map(InputValueDefinition::getType)
            .orElseThrow(() -> new IllegalArgumentException(
                "Fixture mapping references unknown argument '" + argumentName + "' for query operation '" + field.getName() + "'"
            ));
    }

    private String javaQueryExpression(FieldDefinition operation, TypeDefinitionRegistry registry, Map<String, String> dynamicArguments) {
        StringBuilder expression = new StringBuilder("\"");
        appendJavaString(expression, "{ " + operation.getName());
        List<InputValueDefinition> args = operation.getInputValueDefinitions();
        if (args != null && !args.isEmpty()) {
            appendJavaString(expression, "(");
            for (int i = 0; i < args.size(); i++) {
                InputValueDefinition arg = args.get(i);
                if (i > 0) {
                    appendJavaString(expression, ", ");
                }
                appendJavaString(expression, arg.getName() + ": ");
                String dynamic = dynamicArguments.get(arg.getName());
                if (dynamic == null) {
                    appendJavaString(expression, sampleLiteral(arg.getType(), registry));
                } else {
                    expression.append("\" + ").append(dynamic).append(" + \"");
                }
            }
            appendJavaString(expression, ")");
        }
        String selection = selectionSet(operation.getType(), registry, 0, new LinkedHashSet<>());
        if (!selection.isBlank()) {
            appendJavaString(expression, " " + selection);
        }
        appendJavaString(expression, " }");
        expression.append("\"");
        return expression.toString();
    }

    private void appendJavaString(StringBuilder expression, String value) {
        expression.append(javaString(value));
    }

    private String fixtureLiteralExpression(Type<?> type, TypeDefinitionRegistry registry, String variableName) {
        String name = unwrap(type).getName();
        if (BUILTIN_SCALARS.contains(name)) {
            return switch (name) {
                case "Int", "Float", "Boolean" -> "gqlRaw(" + variableName + ")";
                default -> "gqlString(" + variableName + ")";
            };
        }
        if (registry.getType(name, EnumTypeDefinition.class).isPresent()) {
            return "gqlRaw(" + variableName + ")";
        }
        return "gqlString(" + variableName + ")";
    }

    private String typedLiteralExpression(Type<?> type, TypeDefinitionRegistry registry, String variableName) {
        String name = unwrap(type).getName();
        if (BUILTIN_SCALARS.contains(name)) {
            return switch (name) {
                case "Int" -> "gqlInt(" + variableName + ")";
                case "Float" -> "gqlFloat(" + variableName + ")";
                case "Boolean" -> "gqlBoolean(" + variableName + ")";
                default -> "gqlString(" + variableName + ")";
            };
        }
        if (registry.getType(name, EnumTypeDefinition.class).isPresent()) {
            return "gqlRaw(" + variableName + ")";
        }
        return "gqlString(" + variableName + ")";
    }

    private PropertyBinding propertyBinding(List<FieldDefinition> operations, TypeDefinitionRegistry registry) {
        for (FieldDefinition operation : operations) {
            for (InputValueDefinition argument : operation.getInputValueDefinitions()) {
                if (isSupportedPropertyType(argument.getType(), registry)) {
                    return new PropertyBinding(operation, argument);
                }
            }
        }
        return null;
    }

    private boolean isSupportedPropertyType(Type<?> type, TypeDefinitionRegistry registry) {
        TypeName typeName = unwrap(type);
        if (typeName == null) {
            return false;
        }
        String name = typeName.getName();
        return BUILTIN_SCALARS.contains(name) || registry.getType(name, EnumTypeDefinition.class).isPresent();
    }

    private String arbitraryJavaType(Type<?> type, TypeDefinitionRegistry registry) {
        String name = unwrap(type).getName();
        if (registry.getType(name, EnumTypeDefinition.class).isPresent()) {
            return "String";
        }
        return switch (name) {
            case "Int" -> "Integer";
            case "Float" -> "Double";
            case "Boolean" -> "Boolean";
            default -> "String";
        };
    }

    private String propertyProvider(String providerName, InputValueDefinition argument, TypeDefinitionRegistry registry) {
        boolean nullable = !(argument.getType() instanceof NonNullType);
        String name = unwrap(argument.getType()).getName();
        String arbitrary = switch (name) {
            case "Int" -> "GraphQLArbitraries.graphqlInt()";
            case "Float" -> "GraphQLArbitraries.graphqlFloat()";
            case "Boolean" -> "GraphQLArbitraries.graphqlBoolean()";
            case "ID" -> "GraphQLArbitraries.graphqlId()";
            case "String" -> "GraphQLFuzzingArbitraries.sqlInjectionPayloads()";
            default -> {
                Optional<EnumTypeDefinition> enumType = registry.getType(name, EnumTypeDefinition.class);
                if (enumType.isPresent()) {
                    String values = enumType.get().getEnumValueDefinitions().stream()
                        .map(value -> "\"" + javaString(value.getName()) + "\"")
                        .collect(Collectors.joining(", "));
                    yield "Arbitraries.of(" + values + ")";
                }
                yield "GraphQLArbitraries.graphqlString()";
            }
        };
        String type = arbitraryJavaType(argument.getType(), registry);
        if (nullable) {
            arbitrary = "GraphQLArbitraries.nullable(" + arbitrary + ")";
        }
        return "    @Provide\n"
            + "    Arbitrary<" + type + "> " + providerName + "() {\n"
            + "        return " + arbitrary + ";\n"
            + "    }";
    }

    private String graphQlLiteralHelpers() {
        return "\n"
            + "    private static String gqlString(String value) {\n"
            + "        if (value == null) {\n"
            + "            return \"null\";\n"
            + "        }\n"
            + "        return \"\\\"\" + value.replace(\"\\\\\", \"\\\\\\\\\").replace(\"\\\"\", \"\\\\\\\"\") + \"\\\"\";\n"
            + "    }\n\n"
            + "    private static String gqlRaw(String value) {\n"
            + "        return value == null || value.isBlank() ? \"null\" : value;\n"
            + "    }\n\n"
            + "    private static String gqlInt(Integer value) {\n"
            + "        return value == null ? \"null\" : value.toString();\n"
            + "    }\n\n"
            + "    private static String gqlFloat(Double value) {\n"
            + "        return value == null ? \"null\" : value.toString();\n"
            + "    }\n\n"
            + "    private static String gqlBoolean(Boolean value) {\n"
            + "        return value == null ? \"null\" : value.toString();\n"
            + "    }\n";
    }

    private String capitalize(String input) {
        if (input == null || input.isBlank()) {
            return "Value";
        }
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    private String javaString(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private record FixtureTestBinding(String operation, String providerName, List<String> arguments, FieldDefinition field) { }

    private record PropertyBinding(FieldDefinition operation, InputValueDefinition argument) { }
}
