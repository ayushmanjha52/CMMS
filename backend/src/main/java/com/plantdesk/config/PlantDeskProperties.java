package com.plantdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "plantdesk")
public record PlantDeskProperties(Jwt jwt, Cookie cookie, Demo demo, Pm pm) {

    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {}

    public record Cookie(boolean secure) {}

    public record Demo(boolean enabled) {}

    public record Pm(String cron) {}
}
