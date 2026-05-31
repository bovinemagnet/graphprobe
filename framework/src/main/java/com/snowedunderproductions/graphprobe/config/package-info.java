/**
 * Environment configuration utilities for the GraphProbe integration-test framework.
 *
 * <p>This package provides two focused types:
 * <ul>
 *   <li>{@link com.snowedunderproductions.graphprobe.config.EnvConfig} — a static utility
 *       that resolves configuration values from a {@code .env} file and from the
 *       system environment, with typed accessors and mandatory-variable validation.</li>
 *   <li>{@link com.snowedunderproductions.graphprobe.config.TestProfile} — an enum that
 *       selects between the {@code DEFAULT} (full-coverage) and {@code FAST}
 *       (reduced-volume) execution profiles, allowing developers to trade test
 *       thoroughness for speed on a local machine.</li>
 * </ul>
 *
 * <h3>Design principles</h3>
 * <ul>
 *   <li><strong>No hardcoded values.</strong> Every configurable knob — database
 *       URLs, authentication headers, pool sizes — is read from the environment
 *       so the framework remains domain-agnostic and safe to share across projects.</li>
 *   <li><strong>Fail fast on missing required variables.</strong>
 *       {@link com.snowedunderproductions.graphprobe.config.EnvConfig#getRequired(String)}
 *       and
 *       {@link com.snowedunderproductions.graphprobe.config.EnvConfig#validateRequired(String...)}
 *       throw {@link java.lang.IllegalStateException} immediately, surfacing
 *       misconfiguration before any test executes.</li>
 *   <li><strong>One-time initialisation, shared everywhere.</strong>
 *       Both {@code EnvConfig} and {@code TestProfile} initialise their state in a
 *       {@code static} block; subsequent reads are effectively free.</li>
 * </ul>
 *
 * <h3>Consumers</h3>
 * <p>{@code EnvConfig} is used directly by
 * {@link com.snowedunderproductions.graphprobe.database.DatabaseConnectionManager},
 * {@link com.snowedunderproductions.graphprobe.database.HikariConfig},
 * {@link com.snowedunderproductions.graphprobe.client.GraphQLTestClient}, and
 * {@link com.snowedunderproductions.graphprobe.client.SimpleGraphQLClient}.
 * {@code TestProfile} is consulted by
 * {@link com.snowedunderproductions.graphprobe.database.BaseArgumentsProvider} and
 * pagination helpers to clamp row limits and iteration counts when the
 * {@code FAST} profile is active.
 *
 * @see com.snowedunderproductions.graphprobe.database
 * @see com.snowedunderproductions.graphprobe.client
 */
package com.snowedunderproductions.graphprobe.config;
