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
import java.util.function.Function;
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
                                 List<FieldDefinition> queryFields, Map<String, FieldDefinition> queryFieldsByName,
                                 String schemaHash) {
        String basePackage = config.getBasePackage();
        Map<String, String> sources = new LinkedHashMap<>();

        sources.put("GeneratedSchemaSmokeTest.java",
            smokeTest(basePackage, schemaHash, queryFields, registry, config.getDgsCodegenPackage()));

        if (styleEnabled(config.getTestStyle(), "property")) {
            sources.put("GeneratedPropertyTest.java",
                propertyTest(basePackage, schemaHash, queryFields, registry));
        }

        if (styleEnabled(config.getTestStyle(), "fixture") && !config.getFixtureMappings().isEmpty()) {
            List<FixtureTestBinding> fixtureBindings = new ArrayList<>();
            for (Map.Entry<String, FixtureMapping> entry : config.getFixtureMappings().entrySet()) {
                String operation = entry.getKey();
                FixtureMapping mapping = entry.getValue();
                String providerName = JavaNaming.providerName(operation);
                FieldDefinition field = resolveOperationField(operation, queryFieldsByName);
                sources.put("providers/" + providerName + ".java",
                    provider(basePackage, schemaHash, providerName, operation, mapping));
                fixtureBindings.add(new FixtureTestBinding(operation, providerName,
                    new ArrayList<>(mapping.getArguments().keySet()), field));
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

    private String smokeTest(String basePackage, String schemaHash, List<FieldDefinition> operations, TypeDefinitionRegistry registry, String dgsCodegenPackage) {
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
        String methodName = "smoke_" + JavaNaming.safeName(operation.getName());
        String query = queryBuilder.operationQuery(operation, registry);
        String responseFieldExpression = dgsCodegenPackage == null || dgsCodegenPackage.isBlank()
            ? "\"" + JavaNaming.javaString(operation.getName()) + "\""
            : "dgsQueryField(\"" + JavaNaming.dgsConstantName(operation.getName()) + "\", \"" + JavaNaming.javaString(operation.getName()) + "\")";
        return "    @Test\n"
            + "    void " + methodName + "() throws Exception {\n"
            + "        String query = \"\"\"\n" + JavaNaming.indent(query, 8) + "\n        \"\"\";\n"
            + "        String response = CLIENT.executeFullQuery(query);\n"
            + "        JsonNode root = MAPPER.readTree(response);\n"
            + "        assertThat(root.path(\"errors\").isMissingNode() || root.path(\"errors\").isNull() || root.path(\"errors\").isEmpty()).isTrue();\n"
            + "        assertThat(root.path(\"data\").path(" + responseFieldExpression + ").isMissingNode()).isFalse();\n"
            + "    }";
    }

    private String propertyTest(String basePackage, String schemaHash, List<FieldDefinition> operations, TypeDefinitionRegistry registry) {
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
            String variableName = JavaNaming.safeName(binding.argument().getName());
            String providerName = "generated" + JavaNaming.capitalize(variableName);
            String queryExpression = queryBuilder.javaQueryExpression(
                binding.operation(),
                registry,
                Map.of(binding.argument().getName(), queryBuilder.typedLiteralExpression(binding.argument().getType(), registry, variableName))
            );
            String arbitraryType = queryBuilder.arbitraryJavaType(binding.argument().getType(), registry);
            body = "    @GraphQLProperty(tries = 20)\n"
                + "    void property_" + JavaNaming.safeName(binding.operation().getName()) + "(@ForAll(\"" + providerName + "\") " + arbitraryType + " " + variableName + ") throws Exception {\n"
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
            Map<String, String> dynamicArguments = binding.arguments().stream()
                .collect(Collectors.toMap(Function.identity(), arg -> queryBuilder.fixtureLiteralExpression(argumentType(binding.field(), arg), registry, JavaNaming.safeName(arg))));
            String queryExpression = queryBuilder.javaQueryExpression(binding.field(), registry, dynamicArguments);

            return "    @ParameterizedTest\n"
                + "    @DynamicSource(argumentsProvider = " + binding.providerName() + ".class)\n"
                + "    void fixture_" + JavaNaming.safeName(opField) + "(" + args + ") throws Exception {\n"
                + "        String query = " + queryExpression + ";\n"
                + "        String response = CLIENT.executeFullQuery(query);\n"
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
            + "import org.junit.jupiter.params.ParameterizedTest;\n\n"
            + "class GeneratedFixtureBackedTest {\n"
            + "    private static final SimpleGraphQLClient CLIENT = new SimpleGraphQLClient();\n\n"
            + methods + "\n"
            + graphQlLiteralHelpers()
            + "}\n";
    }

    private PropertyBinding propertyBinding(List<FieldDefinition> operations, TypeDefinitionRegistry registry) {
        for (FieldDefinition operation : operations) {
            for (InputValueDefinition argument : operation.getInputValueDefinitions()) {
                if (queryBuilder.isSupportedPropertyType(argument.getType(), registry)) {
                    return new PropertyBinding(operation, argument);
                }
            }
        }
        return null;
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
            + "    private static String dgsQueryField(String constantName, String fallback) {\n"
            + "        try {\n"
            + "            Class<?> queryConstants = Class.forName(\"" + JavaNaming.javaString(dgsCodegenPackage) + ".DgsConstants$QUERY\");\n"
            + "            Object value = queryConstants.getField(constantName).get(null);\n"
            + "            return value instanceof String stringValue ? stringValue : fallback;\n"
            + "        } catch (ReflectiveOperationException | LinkageError ignored) {\n"
            + "            return fallback;\n"
            + "        }\n"
            + "    }\n";
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

    private record FixtureTestBinding(String operation, String providerName, List<String> arguments, FieldDefinition field) { }

    private record PropertyBinding(FieldDefinition operation, InputValueDefinition argument) { }
}
