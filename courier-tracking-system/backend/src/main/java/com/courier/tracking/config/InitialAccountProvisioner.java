package com.courier.tracking.config;

import com.courier.tracking.entity.Role;
import com.courier.tracking.entity.RoleName;
import com.courier.tracking.entity.User;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Bootstrap provisioner for initial administrator and delivery agent accounts.
 * Explicitly gated by app.provisioning.enabled / APP_PROVISIONING_ENABLED environment variable.
 * Credentials are supplied strictly via environment variables.
 * Uses existing BCrypt password encoder, never logs or hardcodes passwords,
 * never overwrites existing account passwords, and skips account creation if already present.
 */
@Component
public class InitialAccountProvisioner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAccountProvisioner.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.provisioning.enabled:${APP_PROVISIONING_ENABLED:false}}")
    private boolean enabled;

    @Value("${app.provisioning.admin.email:${INITIAL_ADMIN_EMAIL:}}")
    private String adminEmail;

    @Value("${app.provisioning.admin.password:${INITIAL_ADMIN_PASSWORD:}}")
    private String adminPassword;

    @Value("${app.provisioning.admin.name:${INITIAL_ADMIN_NAME:System Administrator}}")
    private String adminName;

    @Value("${app.provisioning.admin.phone:${INITIAL_ADMIN_PHONE:}}")
    private String adminPhone;

    @Value("${app.provisioning.agent.email:${INITIAL_AGENT_EMAIL:}}")
    private String agentEmail;

    @Value("${app.provisioning.agent.password:${INITIAL_AGENT_PASSWORD:}}")
    private String agentPassword;

    @Value("${app.provisioning.agent.name:${INITIAL_AGENT_NAME:Initial Delivery Agent}}")
    private String agentName;

    @Value("${app.provisioning.agent.phone:${INITIAL_AGENT_PHONE:}}")
    private String agentPhone;

    public InitialAccountProvisioner(UserRepository userRepository,
                                     RoleRepository roleRepository,
                                     PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("Initial account provisioning disabled (app.provisioning.enabled=false).");
            return;
        }

        log.info("Initial account provisioning is enabled. Checking accounts...");
        provisionAdmin();
        provisionAgent();
    }

    private void provisionAdmin() {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.warn("Privileged ADMIN provisioning skipped: email or password environment variable not set.");
            return;
        }

        String normalizedEmail = adminEmail.toLowerCase(Locale.ROOT).trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            log.info("ADMIN account '{}' already exists. Provisioning skipped to preserve existing credentials.", normalizedEmail);
            return;
        }

        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN role not configured in the database"));

        String encodedPassword = passwordEncoder.encode(adminPassword.trim());
        String name = (adminName != null && !adminName.isBlank()) ? adminName.trim() : "System Administrator";
        String phone = (adminPhone != null && !adminPhone.isBlank()) ? adminPhone.trim() : null;

        User admin = new User(normalizedEmail, encodedPassword, name, phone, adminRole);
        userRepository.save(admin);
        log.info("Successfully provisioned initial ADMIN account: email='{}'", normalizedEmail);
    }

    private void provisionAgent() {
        if (agentEmail == null || agentEmail.isBlank() || agentPassword == null || agentPassword.isBlank()) {
            log.warn("Privileged DELIVERY_AGENT provisioning skipped: email or password environment variable not set.");
            return;
        }

        String normalizedEmail = agentEmail.toLowerCase(Locale.ROOT).trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            log.info("DELIVERY_AGENT account '{}' already exists. Provisioning skipped to preserve existing credentials.", normalizedEmail);
            return;
        }

        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT)
                .orElseThrow(() -> new IllegalStateException("ROLE_DELIVERY_AGENT role not configured in the database"));

        String encodedPassword = passwordEncoder.encode(agentPassword.trim());
        String name = (agentName != null && !agentName.isBlank()) ? agentName.trim() : "Initial Delivery Agent";
        String phone = (agentPhone != null && !agentPhone.isBlank()) ? agentPhone.trim() : null;

        User agent = new User(normalizedEmail, encodedPassword, name, phone, agentRole);
        userRepository.save(agent);
        log.info("Successfully provisioned initial DELIVERY_AGENT account: email='{}'", normalizedEmail);
    }
}
