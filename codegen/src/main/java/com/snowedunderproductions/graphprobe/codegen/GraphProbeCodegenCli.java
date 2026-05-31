package com.snowedunderproductions.graphprobe.codegen;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GraphProbeCodegenCli {
    private GraphProbeCodegenCli() {
    }

    public static void main(String[] args) {
        try {
            CodegenConfig config = parseArgs(args);
            GenerationResult result = new GraphProbeCodegenEngine().generate(config);
            System.out.println("Generated " + result.getGeneratedFiles().size() + " file(s)");
            for (Path generatedFile : result.getGeneratedFiles()) {
                System.out.println(" - " + generatedFile);
            }
            if (!result.getSkippedOperations().isEmpty()) {
                System.err.println("Warning: skipped " + result.getSkippedOperations().size()
                    + " operation(s) beyond the maxOperations limit: " + result.getSkippedOperations());
            }
        } catch (Exception e) {
            System.err.println("graphprobe-codegen: " + e.getMessage());
            System.exit(1);
        }
    }

    static CodegenConfig parseArgs(String[] args) throws IOException {
        CodegenConfig config = new CodegenConfig();
        List<Path> schemaFiles = new ArrayList<>();
        Path configFile = null;
        boolean otherArgs = false;
        boolean operationTypesSpecified = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--config" -> configFile = Path.of(requireValue(args, ++i, arg));
                case "--schema" -> {
                    schemaFiles.add(Path.of(requireValue(args, ++i, arg)));
                    otherArgs = true;
                }
                case "--base-package" -> {
                    config.setBasePackage(requireValue(args, ++i, arg));
                    otherArgs = true;
                }
                case "--output-dir" -> {
                    config.setOutputDirectory(Path.of(requireValue(args, ++i, arg)));
                    otherArgs = true;
                }
                case "--style" -> {
                    config.setTestStyle(requireValue(args, ++i, arg));
                    otherArgs = true;
                }
                case "--include" -> {
                    config.getOperationIncludePatterns().add(requireValue(args, ++i, arg));
                    otherArgs = true;
                }
                case "--exclude" -> {
                    config.getOperationExcludePatterns().add(requireValue(args, ++i, arg));
                    otherArgs = true;
                }
                case "--operation-type" -> {
                    if (!operationTypesSpecified) {
                        config.getOperationTypes().clear();
                        operationTypesSpecified = true;
                    }
                    config.getOperationTypes().add(requireValue(args, ++i, arg));
                    otherArgs = true;
                }
                case "--operation-types" -> {
                    operationTypesSpecified = true;
                    config.setOperationTypes(List.of(requireValue(args, ++i, arg).split(",")));
                    otherArgs = true;
                }
                case "--max-operations" -> {
                    config.setMaxOperations(parseIntArg(requireValue(args, ++i, arg), arg));
                    otherArgs = true;
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        if (configFile != null) {
            if (otherArgs) {
                throw new IllegalArgumentException("--config cannot be combined with other arguments");
            }
            return new CodegenYamlConfigLoader().load(configFile);
        }
        config.setSchemaFiles(schemaFiles);
        return config;
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[index];
    }

    private static int parseIntArg(String value, String flag) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(flag + " requires an integer but got: " + value);
        }
    }
}
