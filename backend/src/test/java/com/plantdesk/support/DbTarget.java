package com.plantdesk.support;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The real PostgreSQL 16 the integration tests run against.
 *
 * <p>Default: a Testcontainers {@code postgres:16-alpine}, started once per JVM. Not H2 —
 * H2 has no row-level security, so isolation tests against it would prove nothing.
 *
 * <p>Override for machines without Docker: set PLANTDESK_TEST_JDBC_URL,
 * PLANTDESK_TEST_DB_USER and PLANTDESK_TEST_DB_PASSWORD to point at any Postgres 16 where
 * that user can create roles. The schema is cleaned and re-migrated at startup.
 */
public record DbTarget(String jdbcUrl, String ownerUser, String ownerPassword) {

    private static DbTarget instance;

    public static synchronized DbTarget get() {
        if (instance == null) {
            String url = System.getenv("PLANTDESK_TEST_JDBC_URL");
            if (url != null && !url.isBlank()) {
                instance = new DbTarget(url, System.getenv("PLANTDESK_TEST_DB_USER"), System.getenv("PLANTDESK_TEST_DB_PASSWORD"));
            } else {
                @SuppressWarnings("resource")
                PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("plantdesk")
                        .withUsername("plantdesk")
                        .withPassword("plantdesk");
                pg.start();
                instance = new DbTarget(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            }
        }
        return instance;
    }
}
