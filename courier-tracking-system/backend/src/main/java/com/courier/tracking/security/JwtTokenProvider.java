package com.courier.tracking.security;

import com.courier.tracking.config.JwtProperties;
import com.courier.tracking.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Service managing JWT generation, cryptographic signing, claims parsing, and validation.
 * Uses JJWT 0.12.6 with HMAC-SHA signing algorithms and enforces strict key length requirements.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String ISSUER = "courier-tracking-system";
    private static final int MIN_KEY_BYTES = 32; // 256 bits for HMAC-SHA256

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        validateConfiguration(jwtProperties);
        this.signingKey = createSigningKey(jwtProperties.getSecret());
    }

    /**
     * Validates that the configured JWT parameters satisfy cryptographic safety requirements.
     * Rejects missing, blank, or insufficiently strong keys at startup.
     */
    private void validateConfiguration(JwtProperties properties) {
        if (properties == null || properties.getSecret() == null || properties.getSecret().isBlank()) {
            throw new IllegalStateException("JWT signing secret (app.jwt.secret / JWT_SECRET) is missing or blank.");
        }
        byte[] keyBytes = resolveKeyBytes(properties.getSecret());
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("JWT signing secret is insufficiently strong. It must provide at least 256 bits (32 bytes) of key material.");
        }
        if (properties.getExpirationMs() <= 0) {
            throw new IllegalStateException("JWT expiration duration (app.jwt.expiration-ms / JWT_EXPIRATION_MS) must be positive.");
        }
    }

    private SecretKey createSigningKey(String secret) {
        byte[] keyBytes = resolveKeyBytes(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] resolveKeyBytes(String secret) {
        if (secret == null) {
            return new byte[0];
        }
        String trimmed = secret.trim();
        try {
            byte[] decoded = Decoders.BASE64.decode(trimmed);
            if (decoded.length >= MIN_KEY_BYTES) {
                return decoded;
            }
        } catch (Exception ignored) {
            // Not valid Base64; fall back to raw UTF-8 bytes
        }
        return trimmed.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Generates a signed JWT access token for the given authenticated User entity.
     * Encapsulates minimal safe claims without exposing passwords, phone numbers, or addresses.
     */
    public String generateToken(User user) {
        return generateToken(user.getId(), user.getEmail(), user.getRole().getName().name());
    }

    /**
     * Generates a signed JWT access token with the specified subject, userId, and role claims.
     */
    public String generateToken(Long userId, String email, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtProperties.getExpirationMs());

        return Jwts.builder()
                .issuer(ISSUER)
                .subject(email)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Cryptographically parses and extracts claims from a signed JWT.
     * Throws JwtException if signature verification fails or token is expired/malformed.
     */
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validates whether a token has a valid signature, structure, and unexpired timestamp.
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT validation failed: token has expired");
            return false;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object userIdObj = extractClaims(token).get("userId");
        if (userIdObj instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    public Date extractExpiration(String token) {
        return extractClaims(token).getExpiration();
    }

    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractExpiration(token);
            return expiration.before(Date.from(Instant.now()));
        } catch (ExpiredJwtException ex) {
            return true;
        } catch (Exception ex) {
            return true;
        }
    }

    public long getExpirationMs() {
        return jwtProperties.getExpirationMs();
    }
}
