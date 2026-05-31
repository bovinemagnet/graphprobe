package com.snowedunderproductions.graphprobe.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

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
    void generatedFixtureTestsCompileAndUseSchemaSelectionSets() throws Exception {
        Path schema = tempDir.resolve("schema.graphqls");
        Files.writeString(schema, """
            type Query {
              user(id: ID!): User
            }

            type User {
              id: ID!
              name: String
            }
            """);

        CodegenConfig config = new CodegenConfig();
        config.setSchemaFiles(List.of(schema));
        config.setBasePackage("com.example.generated.graphprobe");
        config.setOutputDirectory(tempDir.resolve("out"));
        config.setTestStyle("fixture");

        FixtureMapping mapping = new FixtureMapping();
        mapping.setSql("SELECT id FROM users LIMIT 10");
        mapping.getArguments().put("id", "id");
        config.getFixtureMappings().put("Query.user", mapping);

        GenerationResult result = new GraphProbeCodegenEngine().generate(config);

        Path fixtureTest = tempDir.resolve("out/com/example/generated/graphprobe/GeneratedFixtureBackedTest.java");
        assertThat(Files.readString(fixtureTest))
            .contains("import com.example.generated.graphprobe.providers.QueryuserArgumentsProvider;")
            .contains("user(id: ")
            .contains("{ id name }");

        compileGeneratedSources(result);
    }

    @Test
    void generatedPropertyTestsUseTypedArgumentLiteralsAndSelectionSets() throws Exception {
        Path schema = tempDir.resolve("schema.graphqls");
        Files.writeString(schema, """
            type Query {
              users(limit: Int): [User!]
            }

            type User {
              id: ID!
              name: String
            }
            """);

        CodegenConfig config = new CodegenConfig();
        config.setSchemaFiles(List.of(schema));
        config.setBasePackage("com.example.generated.graphprobe");
        config.setOutputDirectory(tempDir.resolve("out"));
        config.setTestStyle("property");

        GenerationResult result = new GraphProbeCodegenEngine().generate(config);

        Path propertyTest = tempDir.resolve("out/com/example/generated/graphprobe/GeneratedPropertyTest.java");
        assertThat(Files.readString(propertyTest))
            .contains("@ForAll(\"generatedLimit\") Integer limit")
            .contains("users(limit: ")
            .contains("{ id name }")
            .doesNotContain("limit: \\\"%s\\\"");

        compileGeneratedSources(result);
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

    @Test
    void dgsCodegenPackageAddsRuntimeConstantsFallback() throws Exception {
        Path schema = tempDir.resolve("schema.graphqls");
        Files.writeString(schema, """
            type Query { user: String }
            """);

        CodegenConfig config = new CodegenConfig();
        config.setSchemaFiles(List.of(schema));
        config.setBasePackage("com.example.generated");
        config.setDgsCodegenPackage("com.example.dgs.generated");
        config.setOutputDirectory(tempDir.resolve("out"));
        config.setTestStyle("smoke");

        GenerationResult result = new GraphProbeCodegenEngine().generate(config);

        Path smokeTest = tempDir.resolve("out/com/example/generated/GeneratedSchemaSmokeTest.java");
        assertThat(Files.readString(smokeTest))
            .contains("Class.forName(\"com.example.dgs.generated.DgsConstants$QUERY\")")
            .contains("dgsQueryField(\"User\", \"user\")");

        compileGeneratedSources(result);
    }

    private void compileGeneratedSources(GenerationResult result) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("Tests must run on a JDK, not a JRE").isNotNull();

        Path classesDir = tempDir.resolve("generated-classes");
        Files.createDirectories(classesDir);

        List<String> args = new ArrayList<>();
        args.add("-classpath");
        args.add(System.getProperty("java.class.path"));
        args.add("-d");
        args.add(classesDir.toString());
        result.getGeneratedFiles().stream()
            .filter(path -> path.toString().endsWith(".java"))
            .map(Path::toString)
            .forEach(args::add);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int exitCode = compiler.run(null, output, output, args.toArray(String[]::new));
        assertThat(exitCode)
            .as(output.toString(StandardCharsets.UTF_8))
            .isZero();
    }
}
