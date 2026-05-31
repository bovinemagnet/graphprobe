# GraphProbe Codegen Sample Consumer

This module is a runnable downstream-style sample for the GraphProbe codegen Gradle plugin.
It generates JUnit tests from `src/main/resources/schema/catalog.graphqls` into
`build/generated/graphprobe-test/java` and compiles those generated sources as part of
the `test` source set.

## What it demonstrates

The `graphProbeCodegen` block in `build.gradle` enables:

| Feature | Configuration |
|---------|---------------|
| Smoke tests | `testStyle = 'all'` generates schema smoke coverage |
| Property tests | Query and mutation variables are exercised with jqwik values |
| Operation types | `operationTypes = ['query', 'mutation']` opts mutations in |
| Fixture-backed tests | `fixtureMappings` maps `Query.product(id:)` to fixture data |

The fixture mapping includes SQL so the generated provider matches the database-backed
runtime shape, plus `csvResource` so this sample can run offline with `USE_CSV=true`.

## Running it

```bash
./gradlew :sample-codegen:test
```

The test task starts an in-build JDK `HttpServer` on an ephemeral loopback port, sets
`GRAPHQL_URL` for the forked test JVM, and sets `USE_CSV=true` so generated fixture tests
use `src/test/resources/graphprobe-fixtures/product-ids.csv`.

To validate the published framework artifact instead of the in-repo project dependency:

```bash
./gradlew :framework:publishToMavenLocal
./gradlew :sample-codegen:test -PuseMavenLocal
```
