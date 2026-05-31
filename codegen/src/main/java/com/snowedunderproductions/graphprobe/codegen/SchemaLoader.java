package com.snowedunderproductions.graphprobe.codegen;

import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

/**
 * Loads and merges GraphQL schema files, resolves operation root types, and
 * computes a stable change-detection hash for the source schemas.
 */
final class SchemaLoader {

    /** Parses and merges all schema files (processed in sorted order for determinism). */
    TypeDefinitionRegistry loadRegistry(List<Path> schemaFiles) throws IOException {
        SchemaParser parser = new SchemaParser();
        TypeDefinitionRegistry combined = new TypeDefinitionRegistry();
        for (Path schemaFile : schemaFiles.stream().sorted().toList()) {
            try {
                combined.merge(parser.parse(Files.readString(schemaFile)));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Failed to parse schema file: " + schemaFile, e);
            }
        }
        return combined;
    }

    /** Resolves the query root type name, honouring an explicit {@code schema { query: ... }} block. */
    String resolveQueryRoot(TypeDefinitionRegistry registry) {
        return resolveOperationRoot(registry, "query").orElse("Query");
    }

    /** Resolves the root type name for the requested operation type. */
    Optional<String> resolveOperationRoot(TypeDefinitionRegistry registry, String operationType) {
        Optional<SchemaDefinition> schemaDefinition = registry.schemaDefinition();
        if (schemaDefinition.isPresent()) {
            for (OperationTypeDefinition operationTypeDefinition : schemaDefinition.get().getOperationTypeDefinitions()) {
                if (operationType.equals(operationTypeDefinition.getName())) {
                    return Optional.of(operationTypeDefinition.getTypeName().getName());
                }
            }
        }
        return switch (operationType) {
            case "query" -> Optional.of("Query");
            case "mutation" -> Optional.of("Mutation");
            case "subscription" -> Optional.of("Subscription");
            default -> Optional.empty();
        };
    }

    String schemaHash(List<Path> schemaFiles) throws IOException {
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
        // Truncated to 48 bits: this is a change-detection tag for the generated header, not a
        // collision-resistant identifier, so a short prefix keeps the header readable.
        return sb.substring(0, 12);
    }
}
