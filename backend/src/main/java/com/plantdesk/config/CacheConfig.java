package com.plantdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

/**
 * Used when CACHE_TYPE=redis (Docker Compose, production). Tests and bare local runs use
 * the in-memory cache, so Redis is never required to run the test suite.
 */
@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .prefixCacheNameWith("plantdesk:")
                .disableCachingNullValues();
    }
}
