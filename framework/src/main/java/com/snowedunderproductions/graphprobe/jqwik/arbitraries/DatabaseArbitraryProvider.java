package com.snowedunderproductions.graphprobe.jqwik.arbitraries;

import com.snowedunderproductions.graphprobe.database.DatabaseConnectionManager;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Arbitraries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridges the shared
 * {@link com.snowedunderproductions.graphprobe.database.DatabaseConnectionManager}
 * connection pool into jqwik by creating {@link net.jqwik.api.Arbitrary} instances
 * whose elements are rows returned by a SQL query.
 *
 * <p>Use this class when a property-based test must operate over values that genuinely
 * exist in the database rather than purely generated ones — for example, to verify that
 * every active user ID can be resolved by a GraphQL resolver.
 *
 * <h3>Usage examples</h3>
 * <pre>{@code
 * // Sample string values from a single column
 * Arbitrary<String> userIds = DatabaseArbitraryProvider
 *     .fromQuery("SELECT user_id FROM users WHERE active = true LIMIT 100")
 *     .extractString("user_id");
 *
 * // Map each row to a domain object
 * Arbitrary<User> users = DatabaseArbitraryProvider
 *     .fromQuery("SELECT id, name, email FROM users LIMIT 50")
 *     .extractObject(rs -> new User(
 *         rs.getString("id"),
 *         rs.getString("name"),
 *         rs.getString("email")
 *     ));
 *
 * // Use in a property test
 * @GraphQLProperty
 * void testWithDatabaseUsers(@ForAll("users") User user) {
 *     assertThat(user.id()).isNotEmpty();
 * }
 *
 * @Provide
 * Arbitrary<User> users() {
 *     return DatabaseArbitraryProvider
 *         .fromQuery("SELECT id, name, email FROM users LIMIT 50")
 *         .extractObject(this::mapUser);
 * }
 * }</pre>
 *
 * <h3>Performance considerations</h3>
 * <ul>
 *   <li>All rows are loaded <em>eagerly</em> when the terminal extract method
 *       ({@link #extractString(String)}, {@link #extractObject(ResultSetExtractor)}, etc.)
 *       is called — not lazily during test execution.</li>
 *   <li>Always include a {@code LIMIT} clause to bound the number of rows fetched and
 *       to keep jqwik's sample space manageable.</li>
 *   <li>The HikariCP connection pool is shared with all other providers; avoid
 *       long-running queries that would hold a connection for an extended period.</li>
 *   <li>For cached, JUnit-integrated test data consider
 *       {@link com.snowedunderproductions.graphprobe.database.BaseArgumentsProvider},
 *       which applies a five-minute Caffeine cache on top of the same pool.</li>
 * </ul>
 *
 * @see com.snowedunderproductions.graphprobe.database.DatabaseConnectionManager
 * @see com.snowedunderproductions.graphprobe.database.BaseArgumentsProvider
 * @see com.snowedunderproductions.graphprobe.jqwik.providers.JqwikArgumentsProvider
 * @see com.snowedunderproductions.graphprobe.jqwik.annotations.GraphQLProperty
 * @since 1.0.0
 */
public final class DatabaseArbitraryProvider {

    private static final Logger log = LoggerFactory.getLogger(DatabaseArbitraryProvider.class);
    private static final DatabaseConnectionManager connectionManager =
        DatabaseConnectionManager.getInstance();

    private final String sql;

    private DatabaseArbitraryProvider(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL query cannot be null or empty");
        }
        this.sql = sql;
    }

    /**
     * Creates a new {@code DatabaseArbitraryProvider} bound to the given SQL query.
     *
     * <p>The query is not executed until one of the terminal extract methods is called
     * (e.g. {@link #extractString(String)}, {@link #extractObject(ResultSetExtractor)}).
     * At that point all matching rows are fetched eagerly from the database.
     * Include a {@code LIMIT} clause to keep query times and memory usage predictable.
     *
     * @param sql the SQL {@code SELECT} query to execute; must not be {@code null} or blank
     * @return a new {@code DatabaseArbitraryProvider} configured with the given query
     * @throws IllegalArgumentException if {@code sql} is {@code null} or blank
     */
    public static DatabaseArbitraryProvider fromQuery(String sql) {
        return new DatabaseArbitraryProvider(sql);
    }

    /**
     * Extracts a single string column from each row as an arbitrary.
     *
     * @param columnName the column name to extract
     * @return arbitrary producing values from the specified column
     */
    public Arbitrary<String> extractString(String columnName) {
        return extractObject(rs -> {
            String value = rs.getString(columnName);
            return value != null ? value : "";
        });
    }

    /**
     * Extracts a single integer column from each row as an arbitrary.
     *
     * @param columnName the column name to extract
     * @return arbitrary producing integer values from the specified column
     */
    public Arbitrary<Integer> extractInt(String columnName) {
        return extractObject(rs -> rs.getInt(columnName));
    }

    /**
     * Extracts a single long column from each row as an arbitrary.
     *
     * @param columnName the column name to extract
     * @return arbitrary producing long values from the specified column
     */
    public Arbitrary<Long> extractLong(String columnName) {
        return extractObject(rs -> rs.getLong(columnName));
    }

    /**
     * Extracts a single double column from each row as an arbitrary.
     *
     * @param columnName the column name to extract
     * @return arbitrary producing double values from the specified column
     */
    public Arbitrary<Double> extractDouble(String columnName) {
        return extractObject(rs -> rs.getDouble(columnName));
    }

    /**
     * Extracts a single boolean column from each row as an arbitrary.
     *
     * @param columnName the column name to extract
     * @return arbitrary producing boolean values from the specified column
     */
    public Arbitrary<Boolean> extractBoolean(String columnName) {
        return extractObject(rs -> rs.getBoolean(columnName));
    }

    /**
     * Extracts multiple columns as a string array from each row.
     *
     * @param columnNames the column names to extract
     * @return arbitrary producing string arrays with values from specified columns
     */
    public Arbitrary<String[]> extractStringArray(String... columnNames) {
        return extractObject(rs -> {
            String[] values = new String[columnNames.length];
            for (int i = 0; i < columnNames.length; i++) {
                values[i] = rs.getString(columnNames[i]);
                if (values[i] == null) {
                    values[i] = "";
                }
            }
            return values;
        });
    }

    /**
     * Extracts custom objects from each result row using the supplied mapping function.
     *
     * <p>This is the most flexible extraction method; all other {@code extract*} helpers
     * delegate to it.  The {@code extractor} is called once per row.  Any row for which
     * the extractor returns {@code null} is silently skipped and excluded from the
     * resulting arbitrary; rows where extraction throws a {@link java.sql.SQLException}
     * are also skipped after a warning is logged.
     *
     * @param <T>       the type of objects to extract
     * @param extractor function that maps the current {@link java.sql.ResultSet} row
     *                  to a value of type {@code T}; may return {@code null} to skip a row
     * @return arbitrary that samples uniformly from all non-null extracted values
     * @throws IllegalStateException if the query returns no rows (or all rows are skipped)
     */
    public <T> Arbitrary<T> extractObject(ResultSetExtractor<T> extractor) {
        List<T> values = executeQuery(extractor);

        if (values.isEmpty()) {
            log.warn("Database query returned no results: {}",
                sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
            throw new IllegalStateException(
                "Database query returned no results. Cannot create arbitrary from empty dataset."
            );
        }

        log.debug("Created arbitrary from {} database rows", values.size());
        return Arbitraries.of(values);
    }

    /**
     * Executes the SQL query and extracts values using the provided extractor.
     *
     * @param <T> the type of values to extract
     * @param extractor function to extract values from ResultSet
     * @return list of extracted values
     */
    private <T> List<T> executeQuery(ResultSetExtractor<T> extractor) {
        ensureConnectionManagerInitialized();

        try {
            return connectionManager.executeWithConnection(connection -> {
                List<T> results = new ArrayList<>();

                try (
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery(sql)
                ) {
                    while (resultSet.next()) {
                        try {
                            T value = extractor.extract(resultSet);
                            if (value != null) {
                                results.add(value);
                            }
                        } catch (SQLException e) {
                            log.warn("Failed to extract value from result set: {}", e.getMessage());
                            // Continue processing other rows
                        }
                    }
                }

                return results;
            });
        } catch (SQLException e) {
            log.error("Failed to execute database query: {}", sql, e);
            throw new RuntimeException("Database query execution failed", e);
        }
    }

    /**
     * Ensures the database connection manager is initialised.
     *
     * @throws IllegalStateException if the connection manager cannot be initialised
     */
    private void ensureConnectionManagerInitialized() {
        if (!connectionManager.isInitialized()) {
            try {
                log.info("Initializing database connection pool for DatabaseArbitraryProvider");
                connectionManager.initialize();

                var healthStatus = connectionManager.getHealthStatus();
                if (!healthStatus.isHealthy()) {
                    throw new IllegalStateException(
                        "Database connection is not healthy: " + healthStatus.getMessage()
                    );
                }
            } catch (Exception e) {
                log.error("Failed to initialize database connection manager", e);
                throw new IllegalStateException(
                    "Cannot create database arbitrary: connection manager initialization failed", e
                );
            }
        }
    }

    /**
     * Functional interface for mapping a single {@link ResultSet} row to a typed value.
     *
     * <p>Implementations are invoked once per result row inside
     * {@link DatabaseArbitraryProvider#extractObject(ResultSetExtractor)}.
     * Returning {@code null} causes the row to be silently excluded from the resulting
     * arbitrary.  Throwing a {@link SQLException} also causes the row to be skipped
     * after a warning is logged.
     *
     * @param <T> the type of value to extract from each row
     */
    @FunctionalInterface
    public interface ResultSetExtractor<T> {
        /**
         * Extracts a value from the current row of the {@link ResultSet}.
         *
         * @param rs the {@link ResultSet} positioned at a valid row; must not be advanced
         *           or closed by the implementation
         * @return the extracted value, or {@code null} to exclude this row from the arbitrary
         * @throws SQLException if a column cannot be accessed or the type mapping fails
         */
        T extract(ResultSet rs) throws SQLException;
    }

    // ==================== Helper Methods ====================

    /**
     * Helper method to safely extract string values (returns empty string for nulls).
     *
     * @param rs the ResultSet
     * @param columnName the column name
     * @return the string value or empty string if null
     * @throws SQLException if column access fails
     */
    public static String safeString(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value != null ? value : "";
    }

    /**
     * Helper method to safely extract long values as strings.
     *
     * @param rs the ResultSet
     * @param columnName the column name
     * @return the long value as string, or "0" if null
     * @throws SQLException if column access fails
     */
    public static String safeLongAsString(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? "0" : String.valueOf(value);
    }

    /**
     * Helper method to safely extract integer values as strings.
     *
     * @param rs the ResultSet
     * @param columnName the column name
     * @return the integer value as string, or "0" if null
     * @throws SQLException if column access fails
     */
    public static String safeIntAsString(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? "0" : String.valueOf(value);
    }

    /**
     * Helper method to safely extract nullable values.
     *
     * @param <T> the value type
     * @param rs the ResultSet
     * @param columnName the column name
     * @param type the Java class of the expected type
     * @return the value or null
     * @throws SQLException if column access fails
     */
    public static <T> T safeNullable(ResultSet rs, String columnName, Class<T> type)
        throws SQLException {
        T value = rs.getObject(columnName, type);
        return rs.wasNull() ? null : value;
    }
}
