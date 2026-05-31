package com.snowedunderproductions.graphprobe.codegen;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure string helpers for turning GraphQL names into safe Java identifiers and
 * for escaping values that are embedded into generated Java source.
 */
final class JavaNaming {

    // Java keywords plus the boolean/null literals — none of these may be used as an identifier.
    private static final Set<String> RESERVED_WORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "true", "false", "null", "_");

    private JavaNaming() {
    }

    /** Sanitises an arbitrary name into a legal, non-reserved Java identifier. */
    static String safeName(String input) {
        String cleaned = input.replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.isEmpty()) {
            return "value";
        }
        if (Character.isDigit(cleaned.charAt(0))) {
            return "_" + cleaned;
        }
        if (RESERVED_WORDS.contains(cleaned)) {
            return cleaned + "_";
        }
        return cleaned;
    }

    /** Derives the generated {@code ArgumentsProvider} class name for an operation. */
    static String providerName(String operation) {
        return operation.replaceAll("[^A-Za-z0-9]", "") + "ArgumentsProvider";
    }

    /** Maps a GraphQL field name to the DGS {@code DgsConstants} field-constant name (PascalCase). */
    static String dgsConstantName(String fieldName) {
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

    static String capitalize(String input) {
        if (input == null || input.isBlank()) {
            return "Value";
        }
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    /** Escapes a string so it can be embedded inside a Java {@code "..."} string literal. */
    static String javaString(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /** Escapes a character so it can be embedded inside a Java {@code '...'} char literal. */
    static String javaChar(char input) {
        return switch (input) {
            case '\\' -> "\\\\";
            case '\'' -> "\\'";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> Character.toString(input);
        };
    }

    static String indent(String input, int spaces) {
        String prefix = " ".repeat(spaces);
        return input.lines().map(line -> prefix + line).collect(Collectors.joining("\n"));
    }
}
