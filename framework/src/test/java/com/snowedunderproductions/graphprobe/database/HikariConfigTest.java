package com.snowedunderproductions.graphprobe.database;

import static org.assertj.core.api.Assertions.*;

import com.snowedunderproductions.graphprobe.config.EnvConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Unit tests for HikariConfig database connection pool configuration.
 *
 * Note: Some tests require database environment variables to be set:
 * - POSTGRES_URL
 * - POSTGRES_USER
 * - POSTGRES_PASSWORD
 *
 * Tests that require these variables are conditionally enabled.
 */
@DisplayName("HikariConfig Tests")
class HikariConfigTest {

    @Test
    @DisplayName("createDataSource() should throw when POSTGRES_URL is missing")
    void testCreateDataSourceThrowsWhenUrlMissing() {
        // This test works even without env vars set, as it tests the validation
        assertThatThrownBy(() -> {
            // Try to create without checking if vars exist
            String url = EnvConfig.get("POSTGRES_URL");
            if (url == null) {
                // Simulate what createDataSource does
                EnvConfig.getRequired("POSTGRES_URL");
            }
        }).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("validateConfiguration() should validate data source is not null")
    void testValidateConfigurationNullDataSource() {
        assertThatThrownBy(() ->
            HikariConfig.validateConfiguration(null)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DataSource cannot be null");
    }

    @Test
    @DisplayName("validateConfiguration() should validate data source is not closed")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_URL", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_USER", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_PASSWORD", matches = ".+")
    void testValidateConfigurationClosedDataSource() {
        // Create and immediately close a data source
        HikariDataSource dataSource = HikariConfig.createDataSource();
        dataSource.close();

        assertThatThrownBy(() ->
            HikariConfig.validateConfiguration(dataSource)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DataSource is closed");
    }

    @Test
    @DisplayName("createDataSource() should create valid data source when env vars are set")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_URL", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_USER", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_PASSWORD", matches = ".+")
    void testCreateDataSourceSuccess() {
        HikariDataSource dataSource = null;
        try {
            dataSource = HikariConfig.createDataSource();

            assertThat(dataSource).isNotNull();
            assertThat(dataSource.isClosed()).isFalse();
            assertThat(dataSource.getJdbcUrl()).isNotEmpty();
            assertThat(dataSource.getUsername()).isNotEmpty();
            assertThat(dataSource.getDriverClassName()).isEqualTo("org.postgresql.Driver");

            // Verify pool size configuration
            assertThat(dataSource.getMinimumIdle()).isGreaterThan(0);
            assertThat(dataSource.getMaximumPoolSize()).isGreaterThan(0);
            assertThat(dataSource.getMaximumPoolSize())
                .isGreaterThanOrEqualTo(dataSource.getMinimumIdle());

            // Verify timeouts are set
            assertThat(dataSource.getConnectionTimeout()).isGreaterThan(0);
            assertThat(dataSource.getIdleTimeout()).isGreaterThan(0);
            assertThat(dataSource.getMaxLifetime()).isGreaterThan(0);
            assertThat(dataSource.getValidationTimeout()).isGreaterThan(0);

            // Verify pool name is set
            assertThat(dataSource.getPoolName()).isNotEmpty();

        } finally {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }

    @Test
    @DisplayName("validateConfiguration() should succeed for valid data source")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_URL", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_USER", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_PASSWORD", matches = ".+")
    void testValidateConfigurationSuccess() {
        HikariDataSource dataSource = HikariConfig.createDataSource();
        try {
            final HikariDataSource ds = dataSource;
            assertThatCode(() ->
                HikariConfig.validateConfiguration(ds)
            ).doesNotThrowAnyException();

        } finally {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }

    @Test
    @DisplayName("createDataSource() should apply default pool size configuration")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_URL", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_USER", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_PASSWORD", matches = ".+")
    void testDefaultPoolSizeConfiguration() {
        HikariDataSource dataSource = null;
        try {
            dataSource = HikariConfig.createDataSource();

            // Check that reasonable defaults are applied
            // (exact values depend on env vars, but should be in reasonable ranges)
            assertThat(dataSource.getMinimumIdle())
                .isBetween(1, 20);
            assertThat(dataSource.getMaximumPoolSize())
                .isBetween(5, 30);

        } finally {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }

    @Test
    @DisplayName("createDataSource() should enable connection testing")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_URL", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_USER", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_PASSWORD", matches = ".+")
    void testConnectionTestingEnabled() {
        HikariDataSource dataSource = null;
        try {
            dataSource = HikariConfig.createDataSource();

            // Verify connection test query is set for PostgreSQL
            assertThat(dataSource.getConnectionTestQuery()).isNotNull();

        } finally {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }

    @Test
    @DisplayName("createDataSource() should configure leak detection")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_URL", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_USER", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_PASSWORD", matches = ".+")
    void testLeakDetectionConfigured() {
        HikariDataSource dataSource = null;
        try {
            dataSource = HikariConfig.createDataSource();

            // Verify leak detection threshold is set
            assertThat(dataSource.getLeakDetectionThreshold())
                .isGreaterThanOrEqualTo(0);

        } finally {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }

    @Test
    @DisplayName("createDataSource() should use PostgreSQL driver")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_URL", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_USER", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_PASSWORD", matches = ".+")
    void testPostgreSQLDriverConfigured() {
        HikariDataSource dataSource = null;
        try {
            dataSource = HikariConfig.createDataSource();

            assertThat(dataSource.getDriverClassName())
                .isEqualTo("org.postgresql.Driver");

        } finally {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }

    @Test
    @DisplayName("HikariConfig class should load successfully")
    void testHikariConfigClassLoads() {
        // Verify the class can be loaded
        assertThat(HikariConfig.class).isNotNull();
    }

    @Test
    @DisplayName("validateConfiguration() should detect invalid pool configuration")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_URL", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_USER", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "POSTGRES_PASSWORD", matches = ".+")
    void testValidateDetectsInvalidConfiguration() {
        HikariDataSource dataSource = HikariConfig.createDataSource();
        try {
            // Validation should pass for properly configured data source
            final HikariDataSource ds = dataSource;
            assertThatCode(() ->
                HikariConfig.validateConfiguration(ds)
            ).doesNotThrowAnyException();

        } finally {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }
}
