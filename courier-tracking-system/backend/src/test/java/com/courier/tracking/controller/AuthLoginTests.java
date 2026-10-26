package com.courier.tracking.controller;

import com.courier.tracking.dto.LoginRequest;
import com.courier.tracking.entity.Role;
import com.courier.tracking.entity.RoleName;
import com.courier.tracking.entity.User;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.UserRepository;
import com.courier.tracking.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthLoginTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private User activeCustomer;
    private User inactiveCustomer;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("ROLE_CUSTOMER must be present"));

        activeCustomer = new User(
                "active.customer@example.com",
                passwordEncoder.encode("SecretP@ssword123"),
                "Active Customer",
                "+1-555-0101",
                customerRole
        );
        userRepository.save(activeCustomer);

        inactiveCustomer = new User(
                "inactive.customer@example.com",
                passwordEncoder.encode("SecretP@ssword123"),
                "Inactive Customer",
                "+1-555-0102",
                customerRole
        );
        inactiveCustomer.setActive(false);
        userRepository.save(inactiveCustomer);
    }

    @Test
    @DisplayName("Verify successful login with valid credentials issues valid Bearer JWT")
    void testSuccessfulLogin() throws Exception {
        LoginRequest request = new LoginRequest("active.customer@example.com", "SecretP@ssword123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.user.email").value("active.customer@example.com"))
                .andExpect(jsonPath("$.data.user.role").value("ROLE_CUSTOMER"))
                .andReturn();

        // Validate JWT token cryptographic authenticity and claims
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = root.get("data").get("accessToken").asText();

        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("active.customer@example.com", jwtTokenProvider.extractEmail(token));
        assertEquals("ROLE_CUSTOMER", jwtTokenProvider.extractRole(token));
        assertEquals(activeCustomer.getId(), jwtTokenProvider.extractUserId(token));

        // Verify password hashes are strictly absent
        JsonNode userData = root.get("data").get("user");
        assertFalse(userData.has("password"));
        assertFalse(userData.has("passwordHash"));
    }

    @Test
    @DisplayName("Verify email normalization during login (case-insensitive and trimmed)")
    void testEmailNormalizationDuringLogin() throws Exception {
        LoginRequest request = new LoginRequest("  ACTIVE.CUSTOMER@EXAMPLE.COM  ", "SecretP@ssword123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("active.customer@example.com"));
    }

    @Test
    @DisplayName("Verify invalid password rejection with generic 401 Unauthorized")
    void testInvalidPasswordRejection() throws Exception {
        LoginRequest request = new LoginRequest("active.customer@example.com", "WrongPassword999!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Verify unknown email rejection with generic 401 Unauthorized")
    void testUnknownEmailRejection() throws Exception {
        LoginRequest request = new LoginRequest("nonexistent.user@example.com", "SecretP@ssword123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Verify deactivated account rejection with 401 Unauthorized")
    void testInactiveAccountRejection() throws Exception {
        LoginRequest request = new LoginRequest("inactive.customer@example.com", "SecretP@ssword123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Account is deactivated. Please contact support."));
    }

    @Test
    @DisplayName("Verify client-supplied role in login payload is ignored and cannot escalate privileges")
    void testRoleInLoginPayloadIsIgnored() throws Exception {
        String payload = """
                {
                    "email": "active.customer@example.com",
                    "password": "SecretP@ssword123",
                    "role": "ROLE_ADMIN"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("ROLE_CUSTOMER"));
    }

    @Test
    @DisplayName("Verify validation error when required fields are missing or invalid")
    void testLoginValidationErrors() throws Exception {
        String invalidPayload = """
                {
                    "email": "not-a-valid-email",
                    "password": ""
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.password").exists());
    }
}
