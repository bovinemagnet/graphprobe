package com.snowedunderproductions.graphprobe.codegen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GraphProbeCodegenCli {
    private GraphProbeCodegenCli() {
    }

    public static void main(String[] args) throws Exception {
        CodegenConfig config = parseArgs(args);
        GenerationResult result = new GraphProbeCodegenEngine().generate(config);
        System.out.println("Generated " + result.getGeneratedFiles().size() + " file(s)");
        for (Path generatedFile : result.getGeneratedFiles()) {
            System.out.println(" - " + generatedFile);
        }
    }

    static CodegenConfig parseArgs(String[] args) throws Exception {
        CodegenConfig config = new CodegenConfig();
        List<Path> schemaFiles = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--config" -> {
                    Path yaml = Path.of(args[++i]);
                    return new CodegenYamlConfigLoader().load(yaml);
                }
                case "--schema" -> schemaFiles.add(Path.of(args[++i]));
                case "--base-package" -> config.setBasePackage(args[++i]);
                case "--output-dir" -> config.setOutputDirectory(Path.of(args[++i]));
                case "--style" -> config.setTestStyle(args[++i]);
                case "--include" -> config.getOperationIncludePatterns().add(args[++i]);
                case "--exclude" -> config.getOperationExcludePatterns().add(args[++i]);
                case "--max-tests" -> config.setMaxGeneratedTestsPerOperation(Integer.parseInt(args[++i]));
                default -> throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        config.setSchemaFiles(schemaFiles);
        return config;
    }
}
