package com.snowedunderproductions.graphprobe.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Static utility for resolving configuration values used by the GraphProbe
 * integration-test framework.
 *
 * <p>Values are sourced from two locations, checked in priority order:
 * <ol>
 *   <li><strong>System environment variables</strong> ({@link System#getenv(String)}) —
 *       highest priority; suitable for CI pipelines and container deployments.</li>
 *   <li><strong>{@code .env} file</strong> — loaded via
 *       <a href="https://github.com/cdimascio/dotenv-java">dotenv-java</a> from the
 *       working directory; silently ignored if the file is absent.</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Required variable — throws IllegalStateException if absent
 * String url = EnvConfig.getRequired("GRAPHQL_URL");
 *
 * // Optional with a default
 * String header = EnvConfig.get("AUTH_HEADER_NAME", "Authorization");
 *
 * // Typed accessors
 * boolean useCsv  = EnvConfig.getBoolean("USE_CSV", false);
 * int     poolMax = EnvConfig.getInt("HIKARI_MAXIMUM_POOL_SIZE", 15);
 *
 * // Bulk validation — throws if any variable is missing
 * EnvConfig.validateRequired("POSTGRES_URL", "POSTGRES_USER", "POSTGRES_PASSWORD");
 * }</pre>
 *
 * <h3>Consumers</h3>
 * <p>This class is used by
 * {@link com.snowedunderproductions.graphprobe.database.HikariConfig},
 * {@link com.snowedunderproductions.graphprobe.database.DatabaseConnectionManager},
 * {@link com.snowedunderproductions.graphprobe.client.GraphQLTestClient},
 * {@link com.snowedunderproductions.graphprobe.client.SimpleGraphQLClient}, and
 * {@link com.snowedunderproductions.graphprobe.config.TestProfile}.
 *
 * <p>All methods are {@code static}; this class is not intended to be instantiated.
 *
 * @see TestProfile
 * @see com.snowedunderproductions.graphprobe.database.HikariConfig
 */
public class EnvConfig {

    private static final Logger log = LoggerFactory.getLogger(EnvConfig.class);
    private static final Dotenv dotenv;

    static {
        // Load .env file if it exists, but don't fail if it doesn't
        dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        log.debug("Environment configuration loaded successfully");
    }

    /**
     * Returns the value of a required environment variable.
     *
     * <p>Delegates to {@link #get(String)} and throws if the result is {@code null}
     * or blank, producing a diagnostic message that names the missing variable.
     *
     * @param key the environment variable name; must not be {@code null}
     * @return the trimmed value; never {@code null} or blank
     * @throws IllegalStateException if the variable is absent or blank in both
     *                               the system environment and the {@code .env} file
     */
    public static String getRequired(String key) {
        String value = get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(
                String.format("Required environment variable '%s' is not set. " +
                    "Please check your .env file or system environment variables.", key)
            );
        }
        return value;
    }

    /**
     * Returns the value of an environment variable, falling back to a default.
     *
     * @param key          the environment variable name; must not be {@code null}
     * @param defaultValue the value to return when the variable is absent;
     *                     may be {@code null}
     * @return the variable's value, or {@code defaultValue} if not found
     */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Returns the value of an environment variable, or {@code null} if absent.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>System environment ({@link System#getenv(String)}) — checked first.</li>
     *   <li>{@code .env} file loaded via dotenv — used as fallback.</li>
     * </ol>
     *
     * @param key the environment variable name; must not be {@code null}
     * @return the variable's value, or {@code null} if not present in either source
     */
    public static String get(String key) {
        // System environment variables take precedence
        String systemValue = System.getenv(key);
        if (systemValue != null) {
            return systemValue;
        }

        // Fall back to .env file
        return dotenv.get(key);
    }

    /**
     * Returns the value of an environment variable wrapped in an {@link Optional}.
     *
     * @param key the environment variable name; must not be {@code null}
     * @return an {@code Optional} containing the value, or {@link Optional#empty()}
     *         if the variable is absent in both the system environment and the
     *         {@code .env} file
     */
    public static Optional<String> getOptional(String key) {
        return Optional.ofNullable(get(key));
    }

    /**
     * Returns a boolean environment variable, falling back to a default.
     *
     * <p>The following string values are recognised (case-insensitive):
     * <ul>
     *   <li>{@code true}, {@code yes}, {@code y}, {@code 1} → {@code true}</li>
     *   <li>{@code false}, {@code no}, {@code n}, {@code 0} → {@code false}</li>
     * </ul>
     * Any other value causes a warning to be logged and {@code defaultValue} to
     * be returned.
     *
     * @param key          the environment variable name; must not be {@code null}
     * @param defaultValue the value to use when the variable is absent or unrecognised
     * @return the resolved boolean value
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }

        String normalized = value.trim().toLowerCase();
        switch (normalized) {
            case "true":
            case "yes":
            case "y":
            case "1":
                return true;
            case "false":
            case "no":
            case "n":
            case "0":
                return false;
            default:
                log.warn("Invalid boolean value '{}' for key '{}', using default: {}",
                    value, key, defaultValue);
                return defaultValue;
        }
    }

    /**
     * Returns an integer environment variable, falling back to a default.
     *
     * <p>If the variable's value cannot be parsed as an integer, a warning is
     * logged and {@code defaultValue} is returned.
     *
     * @param key          the environment variable name; must not be {@code null}
     * @param defaultValue the value to use when the variable is absent or unparseable
     * @return the resolved integer value
     */
    public static int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value '{}' for key '{}', using default: {}",
                value, key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Returns a {@code long} environment variable, falling back to a default.
     *
     * <p>If the variable's value cannot be parsed as a {@code long}, a warning is
     * logged and {@code defaultValue} is returned.
     *
     * @param key          the environment variable name; must not be {@code null}
     * @param defaultValue the value to use when the variable is absent or unparseable
     * @return the resolved long value
     */
    public static long getLong(String key, long defaultValue) {
        String value = get(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid long value '{}' for key '{}', using default: {}",
                value, key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Validates that all specified environment variables are present and non-blank.
     *
     * <p>All missing variables are collected before the exception is thrown, so
     * the error message identifies every gap in a single run rather than failing
     * one at a time.
     *
     * <p>Typical use is in a {@code @BeforeAll} or static initialiser of a test
     * class that depends on several variables:
     * <pre>{@code
     * EnvConfig.validateRequired("POSTGRES_URL", "POSTGRES_USER", "POSTGRES_PASSWORD");
     * }</pre>
     *
     * @param requiredVars one or more environment variable names to check
     * @throws IllegalStateException if one or more variables are absent or blank,
     *                               listing all offenders in the message
     */
    public static void validateRequired(String... requiredVars) {
        StringBuilder missing = new StringBuilder();
        for (String var : requiredVars) {
            if (get(var) == null || get(var).trim().isEmpty()) {
                if (missing.length() > 0) {
                    missing.append(", ");
                }
                missing.append(var);
            }
        }

        if (missing.length() > 0) {
            throw new IllegalStateException(
                String.format("Missing required environment variables: %s. " +
                    "Please configure these values in your .env file or system environment.",
                    missing.toString())
            );
        }
    }
}
