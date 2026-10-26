package com.courier.tracking.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordSecurityTests {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Verify BCrypt password encoder hashes and validates passwords with work factor 12")
    void testBCryptPasswordEncoding() {
        String rawPassword = "StrongSecretPassword#2026";
        String encoded = passwordEncoder.encode(rawPassword);

        assertNotNull(encoded);
        assertTrue(encoded.startsWith("$2a$12$") || encoded.startsWith("$2b$12$"),
                "BCrypt hash should utilize work factor 12");

        assertTrue(passwordEncoder.matches(rawPassword, encoded),
                "Password encoder should successfully match correct raw password");
        assertFalse(passwordEncoder.matches("WrongPassword#999", encoded),
                "Password encoder should reject incorrect password");
    }

    @Test
    @DisplayName("Verify BCrypt uses random salt generating unique hashes for identical raw inputs")
    void testBCryptUniqueSalting() {
        String rawPassword = "ConsistentPassword123";
        String hash1 = passwordEncoder.encode(rawPassword);
        String hash2 = passwordEncoder.encode(rawPassword);

        assertNotEquals(hash1, hash2, "Identical passwords must produce distinct salted hashes");
        assertTrue(passwordEncoder.matches(rawPassword, hash1));
        assertTrue(passwordEncoder.matches(rawPassword, hash2));
    }

    @Test
    @DisplayName("Verify /api/v1/health endpoint remains public without authentication")
    void testHealthEndpointIsPermitted() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Verify unauthenticated requests to protected routes return 401 Unauthorized JSON")
    void testProtectedRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/fleet"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized: Authentication is required to access this resource"));
    }

    @Test
    @DisplayName("Verify JwtProperties binds configured environment settings")
    void testJwtProperties() {
        assertNotNull(jwtProperties.getSecret());
        assertTrue(jwtProperties.getExpirationMs() > 0);
    }
}
