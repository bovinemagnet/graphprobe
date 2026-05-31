# GraphProbe Sample Consumer

A minimal example showing how a downstream test project uses the
**GraphProbe** framework (`com.snowedunderproductions:graphprobe`).

Every test runs **fully offline** — no database and no live GraphQL endpoint — so
the module builds and passes in CI without external infrastructure.

## What it demonstrates

| Test | Framework feature |
|------|-------------------|
| `GraphQLClientSampleTest` | `GraphQLTestClient` query + error handling against an in-process stub endpoint (JDK `HttpServer` on an ephemeral port) |
| `PropertyBasedSampleTest` | `@GraphQLProperty` property-based tests with `GraphQLArbitraries` and `GraphQLFuzzingArbitraries` |
| `DynamicSourceJqwikSampleTest` | `@ParameterizedTest` + `@DynamicSource` fed by jqwik via `JqwikArgumentsProvider` |

JUnit, AssertJ and jqwik are not declared here — they arrive transitively from the
framework's `api` dependencies, exactly as they would for any real consumer.

## Running it

```bash
# Fast feedback: build against the framework source in this repository
gradle21w :sample:test

# Validate the PUBLISHED artifact instead (as an external consumer would):
gradle21w :framework:publishToMavenLocal
gradle21w :sample:test -PuseMavenLocal
```

The `-PuseMavenLocal` flag swaps the in-build `project(':framework')` dependency for
the published `com.snowedunderproductions:graphprobe` coordinate resolved from your
local Maven repository, exercising the published POM and its transitive dependencies.
