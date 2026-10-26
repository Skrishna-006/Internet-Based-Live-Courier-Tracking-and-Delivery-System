package com.courier.tracking.controller;

import com.courier.tracking.dto.CustomerRegistrationRequest;
import com.courier.tracking.entity.RoleName;
import com.courier.tracking.entity.User;
import com.courier.tracking.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerRegistrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Verify successful customer registration with server-assigned ROLE_CUSTOMER")
    void testSuccessfulCustomerRegistration() throws Exception {
        CustomerRegistrationRequest request = new CustomerRegistrationRequest(
                "Jane Doe",
                "jane.doe@example.com",
                "SecureP@ssw0rd2026",
                "+1-555-0199"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Customer account registered successfully"))
                .andExpect(jsonPath("$.data.email").value("jane.doe@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.data.phoneNumber").value("+1-555-0199"))
                .andExpect(jsonPath("$.data.role").value("ROLE_CUSTOMER"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();

        // Verify password hash is strictly excluded from response DTO
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        assertFalse(data.has("password"), "Response must not contain 'password'");
        assertFalse(data.has("passwordHash"), "Response must not contain 'passwordHash'");

        // Verify entity in database
        Optional<User> savedUserOpt = userRepository.findByEmail("jane.doe@example.com");
        assertTrue(savedUserOpt.isPresent());
        User savedUser = savedUserOpt.get();
        assertEquals(RoleName.ROLE_CUSTOMER, savedUser.getRole().getName());

        // Verify password is stored as BCrypt hash and matches raw password
        assertTrue(savedUser.getPasswordHash().startsWith("$2a$12$") || savedUser.getPasswordHash().startsWith("$2b$12$"));
        assertTrue(passwordEncoder.matches("SecureP@ssw0rd2026", savedUser.getPasswordHash()));
        assertFalse(passwordEncoder.matches("WrongPassword", savedUser.getPasswordHash()));
    }

    @Test
    @DisplayName("Verify email addresses are normalized consistently (lowercase & trimmed)")
    void testEmailNormalization() throws Exception {
        CustomerRegistrationRequest request = new CustomerRegistrationRequest(
                "Bob Customer",
                "  Bob.Customer@DOMAIN.com  ",
                "P@ssword12345",
                null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("bob.customer@domain.com"));

        assertTrue(userRepository.existsByEmail("bob.customer@domain.com"));
    }

    @Test
    @DisplayName("Verify duplicate registration with same normalized email is rejected with 409 Conflict")
    void testDuplicateEmailRejection() throws Exception {
        CustomerRegistrationRequest request1 = new CustomerRegistrationRequest(
                "Alice First",
                "alice.unique@example.com",
                "Password123456",
                null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Attempt second registration with mixed case
        CustomerRegistrationRequest duplicateRequest = new CustomerRegistrationRequest(
                "Alice Second",
                "ALICE.UNIQUE@EXAMPLE.COM",
                "DifferentPassword123",
                null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("An account with email 'alice.unique@example.com' already exists."));
    }

    @Test
    @DisplayName("Verify client cannot escalate privileges or supply administrative role in payload")
    void testPrivilegeEscalationPrevented() throws Exception {
        String hostilePayload = """
                {
                    "fullName": "Malicious User",
                    "email": "hacker@example.com",
                    "password": "Password123456",
                    "role": "ROLE_ADMIN",
                    "active": false,
                    "id": 8888,
                    "passwordHash": "$2a$12$fakeHashData"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hostilePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("ROLE_CUSTOMER"))
                .andExpect(jsonPath("$.data.active").value(true));

        User user = userRepository.findByEmail("hacker@example.com").orElseThrow();
        assertEquals(RoleName.ROLE_CUSTOMER, user.getRole().getName(), "Role must be unconditionally ROLE_CUSTOMER");
        assertTrue(user.getActive(), "Account status must be server managed");
        assertNotEquals(8888L, user.getId(), "Database ID must be server-generated");
        assertTrue(passwordEncoder.matches("Password123456", user.getPasswordHash()), "Password must be BCrypt hashed by server");
    }

    @Test
    @DisplayName("Verify invalid registration payloads are rejected with 400 Bad Request")
    void testValidationRejections() throws Exception {
        // Missing name, invalid email, password too short (< 8 chars)
        String invalidPayload = """
                {
                    "fullName": "",
                    "email": "not-an-email",
                    "password": "short"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.fullName").exists())
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.password").exists());
    }
}
