package com.snowedunderproductions.graphprobe.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodegenYamlConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsConfigAndResolvesSchemaPathsRelativeToYamlFile() throws Exception {
        Path yaml = tempDir.resolve("graphprobe.yml");
        Files.writeString(yaml, """
            schemaFiles:
              - schema/api.graphqls
            basePackage: com.example.generated
            testStyle: fixture
            operationTypes:
              - query
              - mutation
            maxOperations: 5
            fixtureMappings:
              Query.user:
                sql: SELECT id FROM users LIMIT 10
                arguments:
                  id: id
            """);

        CodegenConfig config = new CodegenYamlConfigLoader().load(yaml);

        assertThat(config.getBasePackage()).isEqualTo("com.example.generated");
        assertThat(config.getTestStyle()).isEqualTo("fixture");
        assertThat(config.getOperationTypes()).containsExactly("query", "mutation");
        assertThat(config.getMaxOperations()).isEqualTo(5);
        assertThat(config.getSchemaFiles())
            .singleElement()
            .isEqualTo(tempDir.resolve("schema/api.graphqls").normalize());
        assertThat(config.getFixtureMappings()).containsKey("Query.user");
        FixtureMapping mapping = config.getFixtureMappings().get("Query.user");
        assertThat(mapping.getSql()).isEqualTo("SELECT id FROM users LIMIT 10");
        assertThat(mapping.getArguments()).containsEntry("id", "id");
    }

    @Test
    void toleratesExplicitNullCollections() throws Exception {
        Path yaml = tempDir.resolve("graphprobe.yml");
        Files.writeString(yaml, """
            basePackage: com.example.generated
            schemaFiles:
            fixtureMappings:
            """);

        CodegenConfig config = new CodegenYamlConfigLoader().load(yaml);

        assertThat(config.getSchemaFiles()).isEmpty();
        assertThat(config.getFixtureMappings()).isEmpty();
        assertThatCode(() -> config.getFixtureMappings().isEmpty()).doesNotThrowAnyException();
    }
}
