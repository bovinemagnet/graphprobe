package com.snowedunderproductions.graphprobe.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphProbeCodegenEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesSmokePropertyAndFixtureTests() throws Exception {
        Path schema = tempDir.resolve("schema.graphqls");
        Files.writeString(schema, """
            type Query {
              user(id: ID!): User
              users(limit: Int): [User!]
            }

            type User {
              id: ID!
              name: String
              status: String
            }
            """);

        CodegenConfig config = new CodegenConfig();
        config.setSchemaFiles(List.of(schema));
        config.setBasePackage("com.example.generated.graphprobe");
        config.setOutputDirectory(tempDir.resolve("out"));
        config.setTestStyle("all");

        FixtureMapping mapping = new FixtureMapping();
        mapping.setSql("SELECT id FROM users LIMIT 10");
        mapping.setArguments(new LinkedHashMap<>());
        mapping.getArguments().put("id", "id");
        config.setFixtureMappings(new LinkedHashMap<>());
        config.getFixtureMappings().put("Query.user", mapping);

        GenerationResult result = new GraphProbeCodegenEngine().generate(config);

        Path pkg = tempDir.resolve("out/com/example/generated/graphprobe");
        assertThat(result.getGeneratedFiles()).contains(
            pkg.resolve("GeneratedSchemaSmokeTest.java"),
            pkg.resolve("GeneratedPropertyTest.java"),
            pkg.resolve("GeneratedFixtureBackedTest.java"),
            pkg.resolve("providers/QueryuserArgumentsProvider.java")
        );

        assertThat(Files.readString(pkg.resolve("GeneratedSchemaSmokeTest.java")))
            .contains("class GeneratedSchemaSmokeTest")
            .contains("smoke_user")
            .contains("smoke_users");
    }

    @Test
    void generationIsDeterministicForSameInputs() throws Exception {
        Path schema = tempDir.resolve("schema.graphqls");
        Files.writeString(schema, """
            type Query { ping: String }
            """);

        CodegenConfig config = new CodegenConfig();
        config.setSchemaFiles(List.of(schema));
        config.setBasePackage("com.example.generated");
        config.setOutputDirectory(tempDir.resolve("out"));
        config.setTestStyle("smoke");

        GraphProbeCodegenEngine engine = new GraphProbeCodegenEngine();
        engine.generate(config);
        String first = Files.readString(tempDir.resolve("out/com/example/generated/GeneratedSchemaSmokeTest.java"));

        engine.generate(config);
        String second = Files.readString(tempDir.resolve("out/com/example/generated/GeneratedSchemaSmokeTest.java"));

        assertThat(second).isEqualTo(first);
    }
}
