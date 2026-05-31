# GraphProbe — the GraphQL Integration Testing Framework

A shared, reusable testing framework for GraphQL API integration tests using Netflix DGS, Spring Boot, and database-driven test providers.

## Features

- 🔄 **Database-Driven Test Data**: HikariCP connection pooling with Caffeine caching
- 🎯 **Type-Safe GraphQL Clients**: Netflix DGS code generation support
- 🧪 **Parameterized Testing**: Custom JUnit 5 annotations for dynamic test data
- ⚡ **Performance Optimized**: Connection pooling, caching, and build optimizations
- 📊 **Monitoring Ready**: JMX support, health checks, and statistics tracking

## Quick Start

### Installation

Add to your `build.gradle`:

```gradle
dependencies {
    testImplementation 'com.snowedunderproductions:graphprobe:0.0.1'
}
```

### Basic Usage

#### 1. Database-Driven Test Provider

```java
import com.snowedunderproductions.graphprobe.database.BaseArgumentsProvider;

public class UserArgumentsProvider extends BaseArgumentsProvider {
    @Override
    protected String getSQL() {
        return "SELECT id, username, email FROM users LIMIT 10";
    }

    @Override
    protected Arguments extractArguments(ResultSet rs) throws SQLException {
        return Arguments.of(
            getSafeString(rs, "id"),
            getSafeString(rs, "username"),
            getSafeString(rs, "email")
        );
    }

    @Override
    protected String getCacheKey() {
        return "users";
    }
}
```

#### 2. Parameterized GraphQL Test

```java
@ParameterizedTest
@DynamicSource(argumentsProvider = UserArgumentsProvider.class)
void testGetUser(String id, String username, String email) {
    // Generated GraphQL query
    GraphQLQueryRequest request = new GraphQLQueryRequest(
        new GetUserGraphQLQuery.Builder().id(id).build()
    );

    // Execute query
    String result = graphQLClient.executeQuery(request.serialize());

    // Assert results
    assertThat(result).contains(username, email);
}
```

## Environment Configuration

Create a `.env` file or set environment variables:

```bash
# Database
POSTGRES_URL=jdbc:postgresql://localhost:5432/yourdb
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_password

# Optional HikariCP tuning
HIKARI_MINIMUM_IDLE=2
HIKARI_MAXIMUM_POOL_SIZE=10

# Optional SSL
POSTGRES_SSL_MODE=prefer
```

## Core Components

### Database Layer

- `DatabaseConnectionManager`: Singleton HikariCP pool manager
- `HikariConfig`: Optimized PostgreSQL connection configuration
- `BaseArgumentsProvider`: Template for database-driven test data

### Configuration

- `EnvConfig`: Environment variable management with validation

### Annotations

- `@DynamicSource`: Flexible parameterized test data sources
- `@EnabledForVersion`: Version-conditional test execution

## Build & Publish

```bash
# Build the framework
gradle clean build

# Publish to Maven Local
gradle publishToMavenLocal

# Run tests with coverage
gradle test jacocoTestReport
```

## Documentation

- [PRD](docs/GRAPHQL_TEST_FRAMEWORK_PRD.md) - Product Requirements
- [Implementation Progress](docs/IMPLEMENTATION_PROGRESS.md) - Current status
- Migration Guide (coming soon)
- API Reference (coming soon)

## Requirements

- Java 21+
- Gradle 8.0+
- PostgreSQL (for database providers)

## License

MIT License

## Version

**0.0.1** - Initial release
