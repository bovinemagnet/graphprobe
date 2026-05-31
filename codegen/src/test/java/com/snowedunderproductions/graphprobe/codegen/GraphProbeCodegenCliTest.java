package com.snowedunderproductions.graphprobe.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class GraphProbeCodegenCliTest {

    @Test
    void parseArgsUsesDefaultGeneratedOutputDirectory() throws Exception {
        CodegenConfig config = GraphProbeCodegenCli.parseArgs(new String[] {
            "--schema", "schema.graphqls",
            "--base-package", "com.example.generated"
        });

        assertThat(config.getOutputDirectory())
            .isEqualTo(Path.of("build/generated/graphprobe-test/java"));
    }
}
