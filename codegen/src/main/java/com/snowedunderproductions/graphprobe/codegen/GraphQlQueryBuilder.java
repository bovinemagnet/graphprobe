package com.snowedunderproductions.graphprobe.codegen;

import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds GraphQL query strings (both as raw query documents and as Java
 * string-concatenation expressions) and resolves GraphQL types to the Java/jqwik
 * helpers used by the generated tests. Stateless; the schema registry is passed in.
 */
final class GraphQlQueryBuilder {

    private static final Set<String> BUILTIN_SCALARS = Set.of("String", "Int", "Float", "Boolean", "ID");

    /** Maximum nesting depth explored when building a selection set. */
    private static final int MAX_SELECTION_DEPTH = 2;
    /** Maximum number of fields selected per object type to keep generated queries small. */
    private static final int MAX_SELECTION_FIELDS = 5;
    /** Field names preferred (in order) when choosing which fields to select. */
    private static final List<String> PREFERRED_FIELD_NAMES = List.of("id", "name", "status", "title", "code");

    boolean isBuiltinScalar(String name) {
        return BUILTIN_SCALARS.contains(name);
    }

    /** Builds a raw GraphQL query document for a smoke test. */
    String operationQuery(FieldDefinition operation, TypeDefinitionRegistry registry) {
        return operationDocument(new GeneratedOperation("query", "Query", operation), registry, Set.of());
    }

    String operationDocument(GeneratedOperation operation, TypeDefinitionRegistry registry, Set<String> variableArguments) {
        String operationName = generatedOperationName(operation);
        String variables = variableDefinitions(operation.field().getInputValueDefinitions(), variableArguments);
        String args = argumentLiteral(operation.field().getInputValueDefinitions(), registry, variableArguments);
        String selection = selectionSet(operation.field().getType(), registry, 0, new LinkedHashSet<>());
        String field = operation.fieldName() + args + (selection.isBlank() ? "" : " " + selection);
        return operation.operationType() + " " + operationName + variables + " {\n  " + field + "\n}";
    }

    /**
     * Builds a Java expression (a {@code "..."} literal, possibly with {@code + variable +}
     * concatenation) that evaluates to a GraphQL query at test runtime. Arguments present in
     * {@code dynamicArguments} are spliced in as Java expressions; the rest use sample literals.
     */
    String javaQueryExpression(FieldDefinition operation, TypeDefinitionRegistry registry, Map<String, String> dynamicArguments) {
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

    String generatedOperationName(GeneratedOperation operation) {
        return "Generated_" + JavaNaming.safeName(operation.operationType() + "_" + operation.fieldName());
    }

    private void appendJavaString(StringBuilder expression, String value) {
        expression.append(JavaNaming.javaString(value));
    }

    private String argumentLiteral(List<InputValueDefinition> args, TypeDefinitionRegistry registry) {
        return argumentLiteral(args, registry, Set.of());
    }

    private String argumentLiteral(List<InputValueDefinition> args, TypeDefinitionRegistry registry, Set<String> variableArguments) {
        if (args == null || args.isEmpty()) {
            return "";
        }
        return "(" + args.stream()
            .map(arg -> arg.getName() + ": " + (variableArguments.contains(arg.getName()) ? "$" + arg.getName() : sampleLiteral(arg.getType(), registry)))
            .collect(Collectors.joining(", ")) + ")";
    }

    private String variableDefinitions(List<InputValueDefinition> args, Set<String> variableArguments) {
        if (args == null || args.isEmpty() || variableArguments.isEmpty()) {
            return "";
        }
        return args.stream()
            .filter(arg -> variableArguments.contains(arg.getName()))
            .map(arg -> "$" + arg.getName() + ": " + graphQlType(arg.getType()))
            .collect(Collectors.joining(", ", "(", ")"));
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
                if (registry.getType(name, InputObjectTypeDefinition.class).isPresent()) {
                    yield "{}";
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
        if (depth >= MAX_SELECTION_DEPTH || visited.contains(name)) {
            return "{ __typename }";
        }
        Optional<UnionTypeDefinition> unionType = registry.getType(name, UnionTypeDefinition.class);
        if (unionType.isPresent()) {
            return unionSelectionSet(unionType.get(), registry, depth, visited);
        }

        List<FieldDefinition> candidateFields;
        Optional<ObjectTypeDefinition> objectType = registry.getType(name, ObjectTypeDefinition.class);
        if (objectType.isPresent()) {
            candidateFields = objectType.get().getFieldDefinitions();
        } else {
            Optional<InterfaceTypeDefinition> interfaceType = registry.getType(name, InterfaceTypeDefinition.class);
            if (interfaceType.isEmpty()) {
                return "";
            }
            candidateFields = interfaceType.get().getFieldDefinitions();
        }

        visited.add(name);
        List<FieldDefinition> ordered = orderFields(candidateFields);
        List<String> fields = new ArrayList<>();
        for (FieldDefinition field : ordered) {
            if (hasRequiredArguments(field)) {
                continue;
            }
            TypeName fieldTypeName = unwrap(field.getType());
            if (fieldTypeName == null) {
                continue;
            }
            String childType = fieldTypeName.getName();
            if (BUILTIN_SCALARS.contains(childType) || registry.getType(childType, EnumTypeDefinition.class).isPresent()) {
                fields.add(field.getName());
            } else if (depth < MAX_SELECTION_DEPTH - 1) {
                String child = selectionSet(field.getType(), registry, depth + 1, visited);
                if (!child.isBlank()) {
                    fields.add(field.getName() + " " + child);
                }
            }
            if (fields.size() >= MAX_SELECTION_FIELDS) {
                break;
            }
        }
        visited.remove(name);

        if (fields.isEmpty()) {
            return "{ __typename }";
        }

        return "{ " + String.join(" ", fields) + " }";
    }

    private String unionSelectionSet(UnionTypeDefinition unionType, TypeDefinitionRegistry registry, int depth, Set<String> visited) {
        if (depth >= MAX_SELECTION_DEPTH) {
            return "{ __typename }";
        }
        List<String> fragments = unionType.getMemberTypes().stream()
            .map(this::unwrap)
            .filter(typeName -> typeName != null)
            .map(TypeName::getName)
            .filter(typeName -> !visited.contains(typeName))
            .limit(3)
            .map(typeName -> {
                Optional<ObjectTypeDefinition> objectType = registry.getType(typeName, ObjectTypeDefinition.class);
                if (objectType.isEmpty()) {
                    return "";
                }
                String selection = selectionSet(new TypeName(typeName), registry, depth + 1, visited);
                if (selection.isBlank()) {
                    selection = "{ __typename }";
                }
                return "... on " + typeName + " " + selection;
            })
            .filter(fragment -> !fragment.isBlank())
            .toList();
        if (fragments.isEmpty()) {
            return "{ __typename }";
        }
        return "{ __typename " + String.join(" ", fragments) + " }";
    }

    private boolean hasRequiredArguments(FieldDefinition field) {
        return field.getInputValueDefinitions().stream()
            .anyMatch(argument -> argument.getType() instanceof NonNullType);
    }

    private List<FieldDefinition> orderFields(List<FieldDefinition> fields) {
        return fields.stream()
            .sorted(Comparator.comparingInt((FieldDefinition field) -> {
                int idx = PREFERRED_FIELD_NAMES.indexOf(field.getName());
                return idx >= 0 ? idx : PREFERRED_FIELD_NAMES.size() + 1;
            }).thenComparing(FieldDefinition::getName))
            .toList();
    }

    TypeName unwrap(Type<?> type) {
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

    private TypeName requireTypeName(Type<?> type) {
        TypeName typeName = unwrap(type);
        if (typeName == null) {
            throw new IllegalArgumentException("Unsupported GraphQL type, expected a named type but got: " + type);
        }
        return typeName;
    }

    /** The underlying named type, unwrapping non-null/list wrappers; throws if there is none. */
    String requiredTypeName(Type<?> type) {
        return requireTypeName(type).getName();
    }

    /** GraphQL literal helper used by fixture tests (raw for numeric/boolean/enum, quoted otherwise). */
    String fixtureLiteralExpression(Type<?> type, TypeDefinitionRegistry registry, String variableName) {
        String name = requireTypeName(type).getName();
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

    String fixtureVariableExpression(Type<?> type, TypeDefinitionRegistry registry, String variableName) {
        String name = requireTypeName(type).getName();
        if (registry.getType(name, EnumTypeDefinition.class).isPresent()) {
            return variableName;
        }
        return switch (name) {
            case "Int" -> "integerVariable(" + variableName + ")";
            case "Float" -> "doubleVariable(" + variableName + ")";
            case "Boolean" -> "booleanVariable(" + variableName + ")";
            default -> variableName;
        };
    }

    /** GraphQL literal helper used by property tests (typed numeric/boolean helpers). */
    String typedLiteralExpression(Type<?> type, TypeDefinitionRegistry registry, String variableName) {
        String name = requireTypeName(type).getName();
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

    /** Resolves the Java type used for a generated jqwik {@code Arbitrary<T>} parameter. */
    String arbitraryJavaType(Type<?> type, TypeDefinitionRegistry registry) {
        String name = requireTypeName(type).getName();
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

    /** True if the argument type can be driven by a generated property-test arbitrary. */
    boolean isSupportedPropertyType(Type<?> type, TypeDefinitionRegistry registry) {
        if (containsList(type)) {
            return false;
        }
        TypeName typeName = unwrap(type);
        if (typeName == null) {
            return false;
        }
        String name = typeName.getName();
        return BUILTIN_SCALARS.contains(name) || registry.getType(name, EnumTypeDefinition.class).isPresent();
    }

    String sampleVariableExpression(Type<?> type, TypeDefinitionRegistry registry) {
        if (type instanceof NonNullType nonNullType) {
            return sampleVariableExpression(nonNullType.getType(), registry);
        }
        if (type instanceof ListType listType) {
            return "List.of(" + sampleVariableExpression(listType.getType(), registry) + ")";
        }
        String name = requireTypeName(type).getName();
        if (registry.getType(name, EnumTypeDefinition.class).isPresent()) {
            return registry.getType(name, EnumTypeDefinition.class)
                .filter(enumType -> !enumType.getEnumValueDefinitions().isEmpty())
                .map(enumType -> "\"" + JavaNaming.javaString(enumType.getEnumValueDefinitions().get(0).getName()) + "\"")
                .orElse("\"sample\"");
        }
        if (registry.getType(name, InputObjectTypeDefinition.class).isPresent()) {
            return "new LinkedHashMap<String, Object>()";
        }
        return switch (name) {
            case "Int" -> "1";
            case "Float" -> "1.0D";
            case "Boolean" -> "true";
            case "ID" -> "\"1\"";
            case "Date" -> "\"2024-01-01\"";
            case "DateTime" -> "\"2024-01-01T00:00:00Z\"";
            case "BigDecimal" -> "\"1.00\"";
            case "URL" -> "\"https://example.com\"";
            case "Email" -> "\"test@example.com\"";
            default -> "\"sample\"";
        };
    }

    String graphQlType(Type<?> type) {
        if (type instanceof NonNullType nonNullType) {
            return graphQlType(nonNullType.getType()) + "!";
        }
        if (type instanceof ListType listType) {
            return "[" + graphQlType(listType.getType()) + "]";
        }
        return requireTypeName(type).getName();
    }

    private boolean containsList(Type<?> type) {
        if (type instanceof NonNullType nonNullType) {
            return containsList(nonNullType.getType());
        }
        return type instanceof ListType;
    }
}
