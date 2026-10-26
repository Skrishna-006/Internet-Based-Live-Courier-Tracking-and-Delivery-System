package com.courier.tracking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for JWT authentication parameters.
 * Secret is populated via the JWT_SECRET environment variable.
 */
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /**
     * Base64-encoded or raw HMAC-SHA256 secret key.
     */
    private String secret;

    /**
     * Token expiration duration in milliseconds (default: 24 hours).
     */
    private long expirationMs = 86400000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
