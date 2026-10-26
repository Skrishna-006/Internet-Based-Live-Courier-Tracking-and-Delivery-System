package com.courier.tracking.security;

import com.courier.tracking.config.JwtProperties;
import com.courier.tracking.entity.Role;
import com.courier.tracking.entity.RoleName;
import com.courier.tracking.entity.User;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.UserRepository;
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
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtAuthenticationFilterTests {

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
    private JwtProperties jwtProperties;

    private User activeCustomer;
    private User activeAgent;
    private User inactiveUser;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();

        activeCustomer = new User(
                "auth.filter.customer@example.com",
                passwordEncoder.encode("Password123!"),
                "Filter Customer",
                "+1-555-0301",
                customerRole
        );
        userRepository.save(activeCustomer);

        activeAgent = new User(
                "auth.filter.agent@example.com",
                passwordEncoder.encode("Password123!"),
                "Filter Agent",
                "+1-555-0302",
                agentRole
        );
        userRepository.save(activeAgent);

        inactiveUser = new User(
                "auth.filter.disabled@example.com",
                passwordEncoder.encode("Password123!"),
                "Disabled Filter User",
                "+1-555-0303",
                customerRole
        );
        inactiveUser.setActive(false);
        userRepository.save(inactiveUser);
    }

    @Test
    @DisplayName("1. Protected endpoint rejects request with no token with standard 401 Unauthorized")
    void testProtectedEndpointRejectsRequestWithNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized: Authentication is required to access this resource"));
    }

    @Test
    @DisplayName("2. Valid token authenticates the correct user")
    void testValidTokenAuthenticatesUser() throws Exception {
        String token = jwtTokenProvider.generateToken(activeCustomer);

        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("auth.filter.customer@example.com"));
    }

    @Test
    @DisplayName("3. Valid token establishes database-backed authorities")
    void testValidTokenEstablishesDatabaseBackedAuthorities() throws Exception {
        String token = jwtTokenProvider.generateToken(activeAgent);

        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorities", hasItem("ROLE_DELIVERY_AGENT")));
    }

    @Test
    @DisplayName("4. Expired token is rejected with 401 Unauthorized")
    void testExpiredTokenIsRejected() throws Exception {
        JwtProperties expiredProps = new JwtProperties();
        expiredProps.setSecret(jwtProperties.getSecret());
        expiredProps.setExpirationMs(1L); // 1 ms expiration
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(expiredProps);

        String expiredToken = shortLivedProvider.generateToken(activeCustomer);

        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized: Authentication is required to access this resource"));
    }

    @Test
    @DisplayName("5. Tampered token is rejected with 401 Unauthorized")
    void testTamperedTokenIsRejected() throws Exception {
        String validToken = jwtTokenProvider.generateToken(activeCustomer);
        String tamperedToken = validToken.substring(0, validToken.length() - 6) + "xyz123";

        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized: Authentication is required to access this resource"));
    }

    @Test
    @DisplayName("6. Malformed bearer token is safely rejected without 500 error")
    void testMalformedBearerTokenIsRejectedSafely() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer not-a-valid-jwt-token-string"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized: Authentication is required to access this resource"));
    }

    @Test
    @DisplayName("7. Invalid or unsupported authentication schemes cannot authenticate")
    void testInvalidOrUnsupportedSchemeRejected() throws Exception {
        String token = jwtTokenProvider.generateToken(activeCustomer);

        // Basic authentication scheme
        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Basic dXNlcjpwYXNzd29yZA=="))
                .andExpect(status().isUnauthorized());

        // Unsupported 'Token' scheme
        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Token " + token))
                .andExpect(status().isUnauthorized());

        // Empty Bearer prefix
        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("8. Valid token for inactive or disabled account cannot access protected endpoint")
    void testDisabledAccountRejectedEvenWithValidToken() throws Exception {
        String token = jwtTokenProvider.generateToken(inactiveUser);

        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized: Authentication is required to access this resource"));
    }

    @Test
    @DisplayName("9. Public registration, login, and health endpoints work without a token")
    void testPublicEndpointsWorkWithoutToken() throws Exception {
        // Health check
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Login endpoint
        String loginPayload = """
                {
                    "email": "auth.filter.customer@example.com",
                    "password": "Password123!"
                }
                """;
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString());

        // Registration endpoint
        String registerPayload = """
                {
                    "fullName": "New Filter Customer",
                    "email": "filter.registered@example.com",
                    "password": "SecurePassword123!"
                }
                """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }
}
