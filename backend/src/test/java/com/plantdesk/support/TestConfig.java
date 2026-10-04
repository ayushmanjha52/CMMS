package com.plantdesk.support;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
@Import(TestData.class)
public class TestConfig {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock();
    }

    /** Fresh schema per test JVM, so a reused external database starts from V1 every run. */
    @Bean
    public FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
