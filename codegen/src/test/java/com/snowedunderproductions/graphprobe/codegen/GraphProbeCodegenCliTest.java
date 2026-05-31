package com.snowedunderproductions.graphprobe.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphProbeCodegenCliTest {

    @TempDir
    Path tempDir;

    @Test
    void parseArgsUsesDefaultGeneratedOutputDirectory() throws Exception {
        CodegenConfig config = GraphProbeCodegenCli.parseArgs(new String[] {
            "--schema", "schema.graphqls",
            "--base-package", "com.example.generated"
        });

        assertThat(config.getOutputDirectory())
            .isEqualTo(Path.of("build/generated/graphprobe-test/java"));
    }

    @Test
    void parseArgsReadsStyleIncludeAndMaxOperations() throws Exception {
        CodegenConfig config = GraphProbeCodegenCli.parseArgs(new String[] {
            "--schema", "schema.graphqls",
            "--base-package", "com.example.generated",
            "--style", "smoke",
            "--include", "user.*",
            "--max-operations", "7"
        });

        assertThat(config.getTestStyle()).isEqualTo("smoke");
        assertThat(config.getOperationIncludePatterns()).containsExactly("user.*");
        assertThat(config.getMaxOperations()).isEqualTo(7);
    }

    @Test
    void parseArgsLoadsConfigFile() throws Exception {
        Path yaml = tempDir.resolve("graphprobe.yml");
        Files.writeString(yaml, """
            schemaFiles:
              - schema.graphqls
            basePackage: com.example.fromyaml
            """);

        CodegenConfig config = GraphProbeCodegenCli.parseArgs(new String[] {"--config", yaml.toString()});

        assertThat(config.getBasePackage()).isEqualTo("com.example.fromyaml");
    }

    @Test
    void configCannotBeCombinedWithOtherArguments() {
        assertThatThrownBy(() -> GraphProbeCodegenCli.parseArgs(new String[] {
            "--base-package", "com.example.generated",
            "--config", "graphprobe.yml"
        })).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--config cannot be combined");
    }

    @Test
    void missingFlagValueIsReportedClearly() {
        assertThatThrownBy(() -> GraphProbeCodegenCli.parseArgs(new String[] {"--schema"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing value for --schema");
    }

    @Test
    void nonNumericMaxOperationsIsReportedClearly() {
        assertThatThrownBy(() -> GraphProbeCodegenCli.parseArgs(new String[] {"--max-operations", "lots"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("--max-operations requires an integer");
    }

    @Test
    void unknownArgumentIsRejected() {
        assertThatThrownBy(() -> GraphProbeCodegenCli.parseArgs(new String[] {"--bogus"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown argument: --bogus");
    }
}
