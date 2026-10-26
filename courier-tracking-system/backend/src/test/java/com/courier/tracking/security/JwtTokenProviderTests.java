package com.courier.tracking.security;

import com.courier.tracking.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTests {

    private static final String VALID_TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long EXPIRATION_MS = 3600000L; // 1 hour

    private JwtTokenProvider jwtTokenProvider;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(VALID_TEST_SECRET);
        jwtProperties.setExpirationMs(EXPIRATION_MS);
        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
    }

    @Test
    @DisplayName("Verify token generation, signature validity, and required claims")
    void testTokenGenerationAndClaims() {
        String token = jwtTokenProvider.generateToken(101L, "agent@example.com", "ROLE_DELIVERY_AGENT");

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));

        Claims claims = jwtTokenProvider.extractClaims(token);
        assertEquals("courier-tracking-system", claims.getIssuer());
        assertEquals("agent@example.com", claims.getSubject());
        assertEquals("agent@example.com", jwtTokenProvider.extractEmail(token));
        assertEquals(101L, jwtTokenProvider.extractUserId(token));
        assertEquals("ROLE_DELIVERY_AGENT", jwtTokenProvider.extractRole(token));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(new Date()));
        assertFalse(jwtTokenProvider.isTokenExpired(token));

        // Ensure token does NOT contain passwords, phone numbers, or addresses
        assertNull(claims.get("password"));
        assertNull(claims.get("passwordHash"));
        assertNull(claims.get("phoneNumber"));
        assertNull(claims.get("address"));
    }

    @Test
    @DisplayName("Verify tampered token fails signature validation")
    void testTamperedTokenRejection() {
        String validToken = jwtTokenProvider.generateToken(1L, "user@example.com", "ROLE_CUSTOMER");
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "abcde";

        assertFalse(jwtTokenProvider.validateToken(tamperedToken));
    }

    @Test
    @DisplayName("Verify expired token is identified and rejected")
    void testExpiredTokenHandling() {
        JwtProperties expiredProperties = new JwtProperties();
        expiredProperties.setSecret(VALID_TEST_SECRET);
        expiredProperties.setExpirationMs(1L); // 1 ms expiration
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(expiredProperties);

        String token = shortLivedProvider.generateToken(2L, "expired@example.com", "ROLE_CUSTOMER");

        try {
            Thread.sleep(10); // Wait for expiration
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertTrue(shortLivedProvider.isTokenExpired(token));
        assertFalse(shortLivedProvider.validateToken(token));
    }

    @Test
    @DisplayName("Verify startup rejection on missing, blank, or insufficiently strong secret")
    void testInvalidSecretRejection() {
        // Missing (null) secret
        JwtProperties nullSecretProps = new JwtProperties();
        nullSecretProps.setSecret(null);
        IllegalStateException ex1 = assertThrows(IllegalStateException.class,
                () -> new JwtTokenProvider(nullSecretProps));
        assertTrue(ex1.getMessage().contains("missing or blank"));

        // Blank secret
        JwtProperties blankSecretProps = new JwtProperties();
        blankSecretProps.setSecret("   ");
        IllegalStateException ex2 = assertThrows(IllegalStateException.class,
                () -> new JwtTokenProvider(blankSecretProps));
        assertTrue(ex2.getMessage().contains("missing or blank"));

        // Too short secret (< 32 bytes)
        JwtProperties shortSecretProps = new JwtProperties();
        shortSecretProps.setSecret("too-short-secret");
        IllegalStateException ex3 = assertThrows(IllegalStateException.class,
                () -> new JwtTokenProvider(shortSecretProps));
        assertTrue(ex3.getMessage().contains("insufficiently strong"));

        // Non-positive expiration
        JwtProperties zeroExpirationProps = new JwtProperties();
        zeroExpirationProps.setSecret(VALID_TEST_SECRET);
        zeroExpirationProps.setExpirationMs(0);
        IllegalStateException ex4 = assertThrows(IllegalStateException.class,
                () -> new JwtTokenProvider(zeroExpirationProps));
        assertTrue(ex4.getMessage().contains("must be positive"));
    }
}
