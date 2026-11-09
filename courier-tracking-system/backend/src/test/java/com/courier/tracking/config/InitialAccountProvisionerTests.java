package com.courier.tracking.config;

import com.courier.tracking.entity.Role;
import com.courier.tracking.entity.RoleName;
import com.courier.tracking.entity.User;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class InitialAccountProvisionerTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private InitialAccountProvisioner provisioner;

    @Test
    @DisplayName("Disabled provisioning does not create accounts")
    void testProvisioningDisabled() {
        ReflectionTestUtils.setField(provisioner, "enabled", false);
        ReflectionTestUtils.setField(provisioner, "adminEmail", "disabled.admin@college.edu");
        ReflectionTestUtils.setField(provisioner, "adminPassword", "SecureAdminPass#2026");
        ReflectionTestUtils.setField(provisioner, "agentEmail", "disabled.agent@college.edu");
        ReflectionTestUtils.setField(provisioner, "agentPassword", "SecureAgentPass#2026");

        provisioner.run(new DefaultApplicationArguments());

        assertFalse(userRepository.existsByEmail("disabled.admin@college.edu"));
        assertFalse(userRepository.existsByEmail("disabled.agent@college.edu"));
    }

    @Test
    @DisplayName("Enabled provisioning creates privileged ADMIN and DELIVERY_AGENT accounts with BCrypt encoding")
    void testProvisioningSuccess() {
        ReflectionTestUtils.setField(provisioner, "enabled", true);
        ReflectionTestUtils.setField(provisioner, "adminEmail", "Initial.Admin@College.edu");
        ReflectionTestUtils.setField(provisioner, "adminPassword", "AdminSecretPass#2026");
        ReflectionTestUtils.setField(provisioner, "adminName", "Campus Admin Officer");
        ReflectionTestUtils.setField(provisioner, "adminPhone", "+1-555-0100");

        ReflectionTestUtils.setField(provisioner, "agentEmail", "Initial.Agent@College.edu");
        ReflectionTestUtils.setField(provisioner, "agentPassword", "AgentSecretPass#2026");
        ReflectionTestUtils.setField(provisioner, "agentName", "Campus Delivery Courier");
        ReflectionTestUtils.setField(provisioner, "agentPhone", "+1-555-0200");

        provisioner.run(new DefaultApplicationArguments());

        // Verify ADMIN
        Optional<User> adminOpt = userRepository.findByEmail("initial.admin@college.edu");
        assertTrue(adminOpt.isPresent(), "Admin account should be created");
        User admin = adminOpt.get();
        assertEquals("Campus Admin Officer", admin.getFullName());
        assertEquals(RoleName.ROLE_ADMIN, admin.getRole().getName());
        assertTrue(admin.getActive());
        assertNotEquals("AdminSecretPass#2026", admin.getPasswordHash(), "Password must not be stored in plaintext");
        assertTrue(passwordEncoder.matches("AdminSecretPass#2026", admin.getPasswordHash()), "BCrypt password should match");

        // Verify AGENT
        Optional<User> agentOpt = userRepository.findByEmail("initial.agent@college.edu");
        assertTrue(agentOpt.isPresent(), "Agent account should be created");
        User agent = agentOpt.get();
        assertEquals("Campus Delivery Courier", agent.getFullName());
        assertEquals(RoleName.ROLE_DELIVERY_AGENT, agent.getRole().getName());
        assertTrue(agent.getActive());
        assertNotEquals("AgentSecretPass#2026", agent.getPasswordHash(), "Password must not be stored in plaintext");
        assertTrue(passwordEncoder.matches("AgentSecretPass#2026", agent.getPasswordHash()), "BCrypt password should match");
    }

    @Test
    @DisplayName("Provisioning is idempotent and never overwrites existing account passwords")
    void testProvisioningDoesNotOverwriteExisting() {
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
        String originalHash = passwordEncoder.encode("OriginalExistingPassword#1");
        User preExistingAdmin = new User("admin.existing@college.edu", originalHash, "Pre-existing Admin", null, adminRole);
        userRepository.saveAndFlush(preExistingAdmin);

        ReflectionTestUtils.setField(provisioner, "enabled", true);
        ReflectionTestUtils.setField(provisioner, "adminEmail", "admin.existing@college.edu");
        ReflectionTestUtils.setField(provisioner, "adminPassword", "NewAttemptedPassword#99");

        provisioner.run(new DefaultApplicationArguments());

        User reloaded = userRepository.findByEmail("admin.existing@college.edu").orElseThrow();
        assertEquals(originalHash, reloaded.getPasswordHash(), "Existing password hash must NOT be overwritten");
        assertTrue(passwordEncoder.matches("OriginalExistingPassword#1", reloaded.getPasswordHash()));
        assertFalse(passwordEncoder.matches("NewAttemptedPassword#99", reloaded.getPasswordHash()));
    }

    @Test
    @DisplayName("Provisioning skips cleanly when email or password is blank")
    void testProvisioningSkipsWhenBlank() {
        ReflectionTestUtils.setField(provisioner, "enabled", true);
        ReflectionTestUtils.setField(provisioner, "adminEmail", "");
        ReflectionTestUtils.setField(provisioner, "adminPassword", "");
        ReflectionTestUtils.setField(provisioner, "agentEmail", "   ");
        ReflectionTestUtils.setField(provisioner, "agentPassword", null);

        assertDoesNotThrow(() -> provisioner.run(new DefaultApplicationArguments()));
    }
}
