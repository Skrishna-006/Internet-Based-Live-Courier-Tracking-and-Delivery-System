package com.courier.tracking.config;

import com.courier.tracking.CourierTrackingApplication;
import com.courier.tracking.service.ShipmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Production Profile Configuration Validation")
class ProductionProfileConfigTests {

    @Nested
    @DisplayName("Full Context Integration with 'prod' Profile")
    @SpringBootTest(
            classes = CourierTrackingApplication.class,
            properties = {
                    "DB_URL=jdbc:h2:mem:proddb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
                    "DB_USERNAME=sa",
                    "DB_PASSWORD=productionpassword",
                    "DB_DRIVER_CLASS_NAME=org.h2.Driver",
                    "DB_SSL_MODE=disable",
                    "JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
                    "CORS_ALLOWED_ORIGINS=https://courier.example.com",
                    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
            }
    )
    @ActiveProfiles("prod")
    class ProductionContextLoadingTests {

        @Autowired
        private JwtProperties jwtProperties;

        @Autowired
        private ShipmentService shipmentService;

        @Test
        @DisplayName("Production profile loads successfully with valid environment variables")
        void testProdProfile_contextLoadsSuccessfully() {
            assertNotNull(jwtProperties);
            assertEquals("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970", jwtProperties.getSecret());
            assertEquals(86400000L, jwtProperties.getExpirationMs());
        }

        @Test
        @DisplayName("OTP plaintext exposure is strictly disabled in production profile")
        void testProdProfile_otpPlaintextExposureIsDisabled() {
            assertFalse(shipmentService.isExposeOtpInResponse(),
                    "app.otp.expose-in-response must be false in the production profile");
        }
    }

    @Nested
    @DisplayName("Mandatory Secrets Placeholder Resolution Tests")
    class MandatorySecretsResolutionTests {

        private StandardEnvironment createProdEnvironment(Map<String, Object> overrideEnv) throws IOException {
            StandardEnvironment env = new StandardEnvironment();

            // Load application-prod.yml
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            List<PropertySource<?>> sources = loader.load("application-prod", new ClassPathResource("application-prod.yml"));
            for (PropertySource<?> source : sources) {
                env.getPropertySources().addLast(source);
            }

            // Apply environment variables
            if (overrideEnv != null && !overrideEnv.isEmpty()) {
                env.getPropertySources().addFirst(new MapPropertySource("testEnv", overrideEnv));
            }

            return env;
        }

        @Test
        @DisplayName("Fails placeholder resolution when JWT_SECRET is missing")
        void testMissingJwtSecret_failsResolution() throws IOException {
            Map<String, Object> envVars = new HashMap<>();
            envVars.put("DB_URL", "jdbc:postgresql://host:5432/db");
            envVars.put("DB_USERNAME", "prod_user");
            envVars.put("DB_PASSWORD", "prod_pass");
            envVars.put("CORS_ALLOWED_ORIGINS", "https://app.com");
            // JWT_SECRET intentionally omitted

            StandardEnvironment env = createProdEnvironment(envVars);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    env.resolveRequiredPlaceholders(env.getProperty("app.jwt.secret")));

            assertTrue(ex.getMessage().contains("Could not resolve placeholder 'JWT_SECRET'"),
                    "Expected missing JWT_SECRET placeholder error, got: " + ex.getMessage());
        }

        @Test
        @DisplayName("Fails placeholder resolution when DB_URL is missing")
        void testMissingDbUrl_failsResolution() throws IOException {
            Map<String, Object> envVars = new HashMap<>();
            envVars.put("DB_USERNAME", "prod_user");
            envVars.put("DB_PASSWORD", "prod_pass");
            envVars.put("JWT_SECRET", "mysecretkey");
            envVars.put("CORS_ALLOWED_ORIGINS", "https://app.com");
            // DB_URL intentionally omitted

            StandardEnvironment env = createProdEnvironment(envVars);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    env.resolveRequiredPlaceholders(env.getProperty("spring.datasource.url")));

            assertTrue(ex.getMessage().contains("Could not resolve placeholder 'DB_URL'"),
                    "Expected missing DB_URL placeholder error, got: " + ex.getMessage());
        }

        @Test
        @DisplayName("Fails placeholder resolution when DB_PASSWORD is missing")
        void testMissingDbPassword_failsResolution() throws IOException {
            Map<String, Object> envVars = new HashMap<>();
            envVars.put("DB_URL", "jdbc:postgresql://host:5432/db");
            envVars.put("DB_USERNAME", "prod_user");
            envVars.put("JWT_SECRET", "mysecretkey");
            envVars.put("CORS_ALLOWED_ORIGINS", "https://app.com");
            // DB_PASSWORD intentionally omitted

            StandardEnvironment env = createProdEnvironment(envVars);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    env.resolveRequiredPlaceholders(env.getProperty("spring.datasource.password")));

            assertTrue(ex.getMessage().contains("Could not resolve placeholder 'DB_PASSWORD'"),
                    "Expected missing DB_PASSWORD placeholder error, got: " + ex.getMessage());
        }

        @Test
        @DisplayName("Fails placeholder resolution when DB_USERNAME is missing")
        void testMissingDbUsername_failsResolution() throws IOException {
            Map<String, Object> envVars = new HashMap<>();
            envVars.put("DB_URL", "jdbc:postgresql://host:5432/db");
            envVars.put("DB_PASSWORD", "prod_pass");
            envVars.put("JWT_SECRET", "mysecretkey");
            envVars.put("CORS_ALLOWED_ORIGINS", "https://app.com");
            // DB_USERNAME intentionally omitted

            StandardEnvironment env = createProdEnvironment(envVars);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    env.resolveRequiredPlaceholders(env.getProperty("spring.datasource.username")));

            assertTrue(ex.getMessage().contains("Could not resolve placeholder 'DB_USERNAME'"),
                    "Expected missing DB_USERNAME placeholder error, got: " + ex.getMessage());
        }

        @Test
        @DisplayName("Fails placeholder resolution when CORS_ALLOWED_ORIGINS is missing")
        void testMissingCorsAllowedOrigins_failsResolution() throws IOException {
            Map<String, Object> envVars = new HashMap<>();
            envVars.put("DB_URL", "jdbc:postgresql://host:5432/db");
            envVars.put("DB_USERNAME", "prod_user");
            envVars.put("DB_PASSWORD", "prod_pass");
            envVars.put("JWT_SECRET", "mysecretkey");
            // CORS_ALLOWED_ORIGINS intentionally omitted

            StandardEnvironment env = createProdEnvironment(envVars);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    env.resolveRequiredPlaceholders(env.getProperty("app.cors.allowed-origins")));

            assertTrue(ex.getMessage().contains("Could not resolve placeholder 'CORS_ALLOWED_ORIGINS'"),
                    "Expected missing CORS_ALLOWED_ORIGINS placeholder error, got: " + ex.getMessage());
        }

        @Test
        @DisplayName("Resolves default SSL mode to 'require' when DB_SSL_MODE is omitted")
        void testDefaultSslMode_defaultsToRequire() throws IOException {
            StandardEnvironment env = createProdEnvironment(Map.of());
            String sslMode = env.resolvePlaceholders(env.getProperty("spring.datasource.hikari.data-source-properties.sslmode"));
            assertEquals("require", sslMode, "Default production SSL mode must be 'require'");
        }
    }
}
