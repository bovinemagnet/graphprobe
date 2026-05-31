package com.snowedunderproductions.graphprobe.codegen;

import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.NonNullType;
import graphql.language.Type;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Produces the Java source for the generated test classes (smoke, property-based,
 * fixture-backed) and their {@code ArgumentsProvider} helpers. Each method returns
 * source text; file placement and writing are the engine's responsibility.
 */
final class TestSourceGenerator {

    private final GraphQlQueryBuilder queryBuilder;

    TestSourceGenerator(GraphQlQueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    /**
     * Generates all enabled test sources, keyed by their path relative to the base package
     * directory (e.g. {@code GeneratedSchemaSmokeTest.java}, {@code providers/FooArgumentsProvider.java}).
     */
    Map<String, String> generate(CodegenConfig config, TypeDefinitionRegistry registry,
                                 List<GeneratedOperation> operations,
                                 Map<String, GeneratedOperation> operationsByName,
                                 Map<String, GeneratedOperation> operationsByQualifiedName,
                                 String schemaHash) {
        String basePackage = config.getBasePackage();
        Map<String, String> sources = new LinkedHashMap<>();

        sources.put("GeneratedSchemaSmokeTest.java",
            smokeTest(basePackage, schemaHash, operations, registry, config.getDgsCodegenPackage()));

        if (styleEnabled(config.getTestStyle(), "property")) {
            sources.put("GeneratedPropertyTest.java",
                propertyTest(basePackage, schemaHash, operations, registry));
        }

        if (styleEnabled(config.getTestStyle(), "fixture") && !config.getFixtureMappings().isEmpty()) {
            List<FixtureTestBinding> fixtureBindings = new ArrayList<>();
            for (Map.Entry<String, FixtureMapping> entry : config.getFixtureMappings().entrySet()) {
                String operation = entry.getKey();
                FixtureMapping mapping = entry.getValue();
                String providerName = JavaNaming.providerName(operation);
                GeneratedOperation generatedOperation = resolveOperation(operation, operationsByName, operationsByQualifiedName);
                sources.put("providers/" + providerName + ".java",
                    provider(basePackage, schemaHash, providerName, operation, mapping));
                fixtureBindings.add(new FixtureTestBinding(operation, providerName,
                    new ArrayList<>(mapping.getArguments().keySet()), generatedOperation, mapping));
            }
            sources.put("GeneratedFixtureBackedTest.java",
                fixtureTest(basePackage, schemaHash, fixtureBindings, registry));
        }

        return sources;
    }

    private boolean styleEnabled(String style, String requested) {
        String resolved = style == null ? "all" : style.toLowerCase();
        return "all".equals(resolved) || requested.equals(resolved);
    }

    private String smokeTest(String basePackage, String schemaHash, List<GeneratedOperation> operations, TypeDefinitionRegistry registry, String dgsCodegenPackage) {
        String methods = operations.stream()
            .map(op -> smokeMethod(op, registry, dgsCodegenPackage))
            .collect(Collectors.joining("\n\n"));

        return "package " + basePackage + ";\n\n"
            + header(schemaHash)
            + "import static org.assertj.core.api.Assertions.assertThat;\n\n"
            + "import com.fasterxml.jackson.databind.JsonNode;\n"
            + "import com.fasterxml.jackson.databind.ObjectMapper;\n"
            + "import com.snowedunderproductions.graphprobe.client.SimpleGraphQLClient;\n"
            + "import java.util.LinkedHashMap;\n"
            + "import java.util.List;\n"
            + "import java.util.Map;\n"
            + "import org.junit.jupiter.api.Test;\n\n"
            + "class GeneratedSchemaSmokeTest {\n"
            + "    private static final SimpleGraphQLClient CLIENT = new SimpleGraphQLClient();\n"
            + "    private static final ObjectMapper MAPPER = new ObjectMapper();\n\n"
            + methods + "\n"
            + dgsHelper(dgsCodegenPackage)
            + "}\n";
    }

    private String smokeMethod(GeneratedOperation operation, TypeDefinitionRegistry registry, String dgsCodegenPackage) {
        String methodName = "smoke_" + JavaNaming.safeName(operation.operationType() + "_" + operation.fieldName());
        String query = queryBuilder.operationDocument(operation, registry, argumentNames(operation.field()));
        String operationName = queryBuilder.generatedOperationName(operation);
        String responseFieldExpression = dgsCodegenPackage == null || dgsCodegenPackage.isBlank()
            ? "\"" + JavaNaming.javaString(operation.fieldName()) + "\""
            : "dgsOperationField(\"" + operation.operationType().toUpperCase() + "\", \"" + JavaNaming.dgsConstantName(operation.fieldName()) + "\", \"" + JavaNaming.javaString(operation.fieldName()) + "\")";
        return "    @Test\n"
            + "    void " + methodName + "() throws Exception {\n"
            + "        String query = \"\"\"\n" + JavaNaming.indent(query, 8) + "\n        \"\"\";\n"
            + sampleVariables(operation.field(), registry)
            + "        String response = CLIENT.executeFullQuery(query, variables, \"" + operationName + "\");\n"
            + "        JsonNode root = MAPPER.readTree(response);\n"
            + "        assertThat(root.path(\"errors\").isMissingNode() || root.path(\"errors\").isNull() || root.path(\"errors\").isEmpty()).isTrue();\n"
            + "        assertThat(root.path(\"data\").path(" + responseFieldExpression + ").isMissingNode()).isFalse();\n"
            + "    }";
    }

    private String propertyTest(String basePackage, String schemaHash, List<GeneratedOperation> operations, TypeDefinitionRegistry registry) {
        List<PropertyBinding> bindings = propertyBindings(operations, registry);
        String body;
        if (bindings.isEmpty()) {
            body = "    @GraphQLProperty(tries = 10)\n"
                + "    void generatedStringArbitraryIsUsable(@ForAll(\"generatedString\") String value) {\n"
                + "        assertThat(value).isNotNull();\n"
                + "    }\n\n"
                + "    @Provide\n"
                + "    Arbitrary<String> generatedString() {\n"
                + "        return GraphQLArbitraries.graphqlString();\n"
                + "    }";
        } else {
            body = bindings.stream()
                .map(binding -> propertyMethod(binding, registry) + "\n\n" + propertyProvider(propertyProviderName(binding), binding.argument(), registry))
                .collect(Collectors.joining("\n\n"));
        }

        return "package " + basePackage + ";\n\n"
            + header(schemaHash)
            + "import static org.assertj.core.api.Assertions.assertThat;\n\n"
            + "import com.snowedunderproductions.graphprobe.client.SimpleGraphQLClient;\n"
            + "import com.snowedunderproductions.graphprobe.jqwik.annotations.GraphQLProperty;\n"
            + "import com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLArbitraries;\n"
            + "import com.snowedunderproductions.graphprobe.jqwik.arbitraries.GraphQLFuzzingArbitraries;\n"
            + "import java.util.LinkedHashMap;\n"
            + "import java.util.Map;\n"
            + "import net.jqwik.api.Arbitrary;\n"
            + "import net.jqwik.api.Arbitraries;\n"
            + "import net.jqwik.api.ForAll;\n"
            + "import net.jqwik.api.Provide;\n\n"
            + "class GeneratedPropertyTest {\n"
            + "    private static final SimpleGraphQLClient CLIENT = new SimpleGraphQLClient();\n\n"
            + body + "\n\n"
            + "}\n";
    }

    private String propertyMethod(PropertyBinding binding, TypeDefinitionRegistry registry) {
        GeneratedOperation operation = binding.operation();
        InputValueDefinition argument = binding.argument();
        String variableName = JavaNaming.safeName(argument.getName());
        String providerName = propertyProviderName(binding);
        String query = queryBuilder.operationDocument(operation, registry, Set.of(argument.getName()));
        String operationName = queryBuilder.generatedOperationName(operation);
        String arbitraryType = queryBuilder.arbitraryJavaType(argument.getType(), registry);
        return "    @GraphQLProperty(tries = 20)\n"
            + "    void property_" + JavaNaming.safeName(operation.operationType() + "_" + operation.fieldName() + "_" + argument.getName())
            + "(@ForAll(\"" + providerName + "\") " + arbitraryType + " " + variableName + ") throws Exception {\n"
            + "        String query = \"\"\"\n" + JavaNaming.indent(query, 8) + "\n        \"\"\";\n"
            + "        Map<String, Object> variables = new LinkedHashMap<>();\n"
            + "        variables.put(\"" + JavaNaming.javaString(argument.getName()) + "\", " + variableName + ");\n"
            + "        String response = CLIENT.executeFullQuery(query, variables, \"" + operationName + "\");\n"
            + "        assertThat(response).isNotBlank();\n"
            + "    }";
    }

    private String provider(String basePackage, String schemaHash, String providerName, String operation, FixtureMapping mapping) {
        List<String> args = new ArrayList<>();
        if (mapping.getArguments() != null && !mapping.getArguments().isEmpty()) {
            mapping.getArguments().forEach((argument, column) -> args.add("            getSafeString(rs, \"" + JavaNaming.javaString(column) + "\")"));
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
            + "        return \"" + JavaNaming.javaString(sql) + "\";\n"
            + "    }\n\n"
            + "    @Override\n"
            + "    protected Arguments extractArguments(ResultSet rs) throws SQLException {\n"
            + "        return Arguments.of(\n"
            + String.join(",\n", args)
            + "\n        );\n"
            + "    }\n\n"
            + "    @Override\n"
            + "    protected String getCacheKey() {\n"
            + "        return \"" + JavaNaming.javaString(operation) + "\";\n"
            + "    }\n"
            + "}\n";
    }

    private String fixtureTest(String basePackage, String schemaHash, List<FixtureTestBinding> bindings, TypeDefinitionRegistry registry) {
        String methods = bindings.stream().map(binding -> {
            String args = binding.arguments().isEmpty()
                ? "String value"
                : binding.arguments().stream().map(arg -> "String " + JavaNaming.safeName(arg)).collect(Collectors.joining(", "));

            String opField = binding.operation().contains(".") ? binding.operation().substring(binding.operation().indexOf('.') + 1) : binding.operation();
            String query = queryBuilder.operationDocument(binding.operationDefinition(), registry, Set.copyOf(binding.arguments()));
            String operationName = queryBuilder.generatedOperationName(binding.operationDefinition());

            return "    @ParameterizedTest\n"
                + "    " + dynamicSourceAnnotation(binding) + "\n"
                + "    void fixture_" + JavaNaming.safeName(opField) + "(" + args + ") throws Exception {\n"
                + "        String query = \"\"\"\n" + JavaNaming.indent(query, 8) + "\n        \"\"\";\n"
                + fixtureVariables(binding, registry)
                + "        String response = CLIENT.executeFullQuery(query, variables, \"" + operationName + "\");\n"
                + "        assertThat(response).isNotBlank();\n"
                + "    }";
        }).collect(Collectors.joining("\n\n"));
        String providerImports = bindings.stream()
            .map(binding -> "import " + basePackage + ".providers." + binding.providerName() + ";\n")
            .distinct()
            .collect(Collectors.joining());

        return "package " + basePackage + ";\n\n"
            + header(schemaHash)
            + "import static org.assertj.core.api.Assertions.assertThat;\n\n"
            + "import com.snowedunderproductions.graphprobe.annotations.DynamicSource;\n"
            + "import com.snowedunderproductions.graphprobe.client.SimpleGraphQLClient;\n"
            + providerImports
            + "import java.util.LinkedHashMap;\n"
            + "import java.util.Map;\n"
            + "import org.junit.jupiter.params.ParameterizedTest;\n\n"
            + "class GeneratedFixtureBackedTest {\n"
            + "    private static final SimpleGraphQLClient CLIENT = new SimpleGraphQLClient();\n\n"
            + methods + "\n"
            + variableConversionHelpers()
            + "}\n";
    }

    private String dynamicSourceAnnotation(FixtureTestBinding binding) {
        FixtureMapping mapping = binding.mapping();
        if (mapping.getCsvResource() == null || mapping.getCsvResource().isBlank()) {
            return "@DynamicSource(argumentsProvider = " + binding.providerName() + ".class)";
        }
        return "@DynamicSource(argumentsProvider = " + binding.providerName()
            + ".class, csvResource = \"" + JavaNaming.javaString(mapping.getCsvResource())
            + "\", delimiter = '" + JavaNaming.javaChar(mapping.getDelimiter())
            + "', linesToSkip = " + mapping.getLinesToSkip() + ")";
    }

    private List<PropertyBinding> propertyBindings(List<GeneratedOperation> operations, TypeDefinitionRegistry registry) {
        List<PropertyBinding> bindings = new ArrayList<>();
        for (GeneratedOperation operation : operations) {
            for (InputValueDefinition argument : operation.field().getInputValueDefinitions()) {
                if (queryBuilder.isSupportedPropertyType(argument.getType(), registry)) {
                    bindings.add(new PropertyBinding(operation, argument));
                }
            }
        }
        return bindings;
    }

    private String propertyProviderName(PropertyBinding binding) {
        return "generated"
            + JavaNaming.capitalize(JavaNaming.safeName(binding.operation().operationType()))
            + JavaNaming.capitalize(JavaNaming.safeName(binding.operation().fieldName()))
            + JavaNaming.capitalize(JavaNaming.safeName(binding.argument().getName()));
    }

    private String propertyProvider(String providerName, InputValueDefinition argument, TypeDefinitionRegistry registry) {
        boolean nullable = !(argument.getType() instanceof NonNullType);
        String name = queryBuilder.requiredTypeName(argument.getType());
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
                        .map(value -> "\"" + JavaNaming.javaString(value.getName()) + "\"")
                        .collect(Collectors.joining(", "));
                    yield "Arbitraries.of(" + values + ")";
                }
                yield "GraphQLArbitraries.graphqlString()";
            }
        };
        String type = queryBuilder.arbitraryJavaType(argument.getType(), registry);
        if (nullable) {
            arbitrary = "GraphQLArbitraries.nullable(" + arbitrary + ")";
        }
        return "    @Provide\n"
            + "    Arbitrary<" + type + "> " + providerName + "() {\n"
            + "        return " + arbitrary + ";\n"
            + "    }";
    }

    private GeneratedOperation resolveOperation(String operation, Map<String, GeneratedOperation> operationsByName,
                                                Map<String, GeneratedOperation> operationsByQualifiedName) {
        GeneratedOperation qualified = operationsByQualifiedName.get(operation);
        if (qualified != null) {
            return qualified;
        }
        String fieldName = operation.contains(".") ? operation.substring(operation.indexOf('.') + 1) : operation;
        GeneratedOperation field = operationsByName.get(fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Fixture mapping references unknown or excluded query operation: " + operation);
        }
        return field;
    }

    private Type<?> argumentType(GeneratedOperation operation, String argumentName) {
        return operation.field().getInputValueDefinitions().stream()
            .filter(argument -> argument.getName().equals(argumentName))
            .findFirst()
            .map(InputValueDefinition::getType)
            .orElseThrow(() -> new IllegalArgumentException(
                "Fixture mapping references unknown argument '" + argumentName + "' for operation '" + operation.qualifiedName() + "'"
            ));
    }

    private String header(String schemaHash) {
        return "/*\n"
            + " * Generated by GraphProbe codegen\n"
            + " * Generator version: 1\n"
            + " * Source schema hash: " + schemaHash + "\n"
            + " */\n\n";
    }

    private String dgsHelper(String dgsCodegenPackage) {
        if (dgsCodegenPackage == null || dgsCodegenPackage.isBlank()) {
            return "";
        }
        return "\n"
            + "    private static String dgsOperationField(String operationType, String constantName, String fallback) {\n"
            + "        try {\n"
            + "            Class<?> operationConstants = Class.forName(\"" + JavaNaming.javaString(dgsCodegenPackage) + ".DgsConstants$\" + operationType);\n"
            + "            Object value = operationConstants.getField(constantName).get(null);\n"
            + "            return value instanceof String stringValue ? stringValue : fallback;\n"
            + "        } catch (ReflectiveOperationException | LinkageError ignored) {\n"
            + "            return fallback;\n"
            + "        }\n"
            + "    }\n";
    }

    private Set<String> argumentNames(FieldDefinition field) {
        return field.getInputValueDefinitions().stream()
            .map(InputValueDefinition::getName)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private String sampleVariables(FieldDefinition field, TypeDefinitionRegistry registry) {
        StringBuilder source = new StringBuilder("        Map<String, Object> variables = new LinkedHashMap<>();\n");
        for (InputValueDefinition argument : field.getInputValueDefinitions()) {
            source.append("        variables.put(\"")
                .append(JavaNaming.javaString(argument.getName()))
                .append("\", ")
                .append(queryBuilder.sampleVariableExpression(argument.getType(), registry))
                .append(");\n");
        }
        return source.toString();
    }

    private String fixtureVariables(FixtureTestBinding binding, TypeDefinitionRegistry registry) {
        StringBuilder source = new StringBuilder("        Map<String, Object> variables = new LinkedHashMap<>();\n");
        for (String argument : binding.arguments()) {
            source.append("        variables.put(\"")
                .append(JavaNaming.javaString(argument))
                .append("\", ")
                .append(queryBuilder.fixtureVariableExpression(argumentType(binding.operationDefinition(), argument), registry, JavaNaming.safeName(argument)))
                .append(");\n");
        }
        return source.toString();
    }

    private String variableConversionHelpers() {
        return "\n"
            + "    private static Integer integerVariable(String value) {\n"
            + "        if (value == null) {\n"
            + "            return null;\n"
            + "        }\n"
            + "        return value.isBlank() ? null : Integer.valueOf(value);\n"
            + "    }\n\n"
            + "    private static Double doubleVariable(String value) {\n"
            + "        if (value == null) {\n"
            + "            return null;\n"
            + "        }\n"
            + "        return value.isBlank() ? null : Double.valueOf(value);\n"
            + "    }\n\n"
            + "    private static Boolean booleanVariable(String value) {\n"
            + "        if (value == null) {\n"
            + "            return null;\n"
            + "        }\n"
            + "        return value.isBlank() ? null : Boolean.valueOf(value);\n"
            + "    }\n";
    }

    private record FixtureTestBinding(String operation, String providerName, List<String> arguments,
                                      GeneratedOperation operationDefinition, FixtureMapping mapping) { }

    private record PropertyBinding(GeneratedOperation operation, InputValueDefinition argument) { }
}
