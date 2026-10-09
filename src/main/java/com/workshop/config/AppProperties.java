package com.workshop.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Type-safe view of the {@code app.*} properties. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Admin admin, Mail mail, String timezone) {

    public record Jwt(String secret, long expirationMinutes) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Admin(String name, String email, String password) {
    }

    public record Mail(String from) {
    }
}
