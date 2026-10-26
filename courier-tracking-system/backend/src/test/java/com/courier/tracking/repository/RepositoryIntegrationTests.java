package com.courier.tracking.repository;

import com.courier.tracking.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RepositoryIntegrationTests {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private TrackingEventRepository trackingEventRepository;

    @Autowired
    private DeliveryProofRepository deliveryProofRepository;

    @Test
    @DisplayName("Verify Flyway V2 seeds standard roles without seeding users")
    void testStandardRolesSeeded() {
        Optional<Role> customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER);
        Optional<Role> agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT);
        Optional<Role> adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN);

        assertTrue(customerRole.isPresent(), "ROLE_CUSTOMER should be present");
        assertTrue(agentRole.isPresent(), "ROLE_DELIVERY_AGENT should be present");
        assertTrue(adminRole.isPresent(), "ROLE_ADMIN should be present");

        // Verify no user credentials were artificially seeded
        assertEquals(0, userRepository.count(), "No users should be auto-seeded in migrations");
    }

    @Test
    @DisplayName("Verify user persistence with email normalization and unique constraint")
    void testUserPersistenceAndEmailNormalization() {
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();

        // Create user with mixed-case email and whitespace
        User user = new User("  Alice.Consignor@Domain.COM  ", "hash12345", "Alice Consignor", "+1234567890", customerRole);
        User saved = userRepository.saveAndFlush(user);

        assertNotNull(saved.getId());
        assertEquals("alice.consignor@domain.com", saved.getEmail(), "Email should be saved in lowercase normalized form");
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertTrue(saved.getActive());

        // Attempt duplicate registration with same email in different case
        User duplicate = new User("ALICE.CONSIGNOR@DOMAIN.COM", "otherhash", "Alice Duplicate", "+1234567890", customerRole);
        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.saveAndFlush(duplicate);
        }, "Database unique constraint on normalized email should reject duplicate user");
    }

    @Test
    @DisplayName("Verify shipment persistence, agent assignment, and tracking queries")
    void testShipmentLifecyclePersistence() {
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();

        User customer = userRepository.saveAndFlush(new User("cust@example.com", "hash1", "John Doe", "555-100", customerRole));
        User agent = userRepository.saveAndFlush(new User("agent@example.com", "hash2", "Driver Bob", "555-200", agentRole));

        Shipment shipment = new Shipment(
                "TRK-20261026-9A1B2",
                customer,
                "John Doe", "123 Origin St", "555-100",
                "Jane Smith", "456 Destination Ave", "555-999",
                new BigDecimal("2.500"),
                new BigDecimal("49.99")
        );
        shipment.setAssignedAgent(agent);
        shipment.setStatus(ShipmentStatus.ASSIGNED);

        Shipment savedShipment = shipmentRepository.saveAndFlush(shipment);
        assertNotNull(savedShipment.getId());
        assertEquals("TRK-20261026-9A1B2", savedShipment.getTrackingNumber());
        assertEquals(ShipmentStatus.ASSIGNED, savedShipment.getStatus());

        // Query by tracking number
        Optional<Shipment> queried = shipmentRepository.findByTrackingNumber("TRK-20261026-9A1B2");
        assertTrue(queried.isPresent());
        assertEquals(customer.getId(), queried.get().getCustomer().getId());
        assertEquals(agent.getId(), queried.get().getAssignedAgent().getId());

        // Query by assigned agent
        List<Shipment> agentShipments = shipmentRepository.findByAssignedAgentIdAndStatus(agent.getId(), ShipmentStatus.ASSIGNED);
        assertEquals(1, agentShipments.size());
    }

    @Test
    @DisplayName("Verify tracking event history is appended chronologically")
    void testTrackingEventAuditHistory() {
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        User customer = userRepository.saveAndFlush(new User("sender@domain.com", "hash", "Sender", "555-111", customerRole));

        Shipment shipment = shipmentRepository.saveAndFlush(new Shipment(
                "TRK-EVENT-001", customer, "Sender", "Addr 1", "555-111",
                "Receiver", "Addr 2", "555-222", new BigDecimal("1.000"), new BigDecimal("19.99")
        ));

        // Append multiple events
        TrackingEvent e1 = new TrackingEvent(shipment, ShipmentStatus.CREATED, new BigDecimal("40.7128"), new BigDecimal("-74.0060"), "Origin Terminal", "Parcel created");
        TrackingEvent e2 = new TrackingEvent(shipment, ShipmentStatus.PICKED_UP, new BigDecimal("40.7130"), new BigDecimal("-74.0055"), "Courier Van 12", "Scanned by courier");
        TrackingEvent e3 = new TrackingEvent(shipment, ShipmentStatus.IN_TRANSIT, new BigDecimal("40.7500"), new BigDecimal("-73.9800"), "Regional Hub A", "Arrived at sorting facility");

        trackingEventRepository.saveAllAndFlush(List.of(e1, e2, e3));

        List<TrackingEvent> history = trackingEventRepository.findByShipmentTrackingNumberOrderByEventTimestampAsc("TRK-EVENT-001");
        assertEquals(3, history.size());
        assertEquals(ShipmentStatus.CREATED, history.get(0).getStatus());
        assertEquals(ShipmentStatus.PICKED_UP, history.get(1).getStatus());
        assertEquals(ShipmentStatus.IN_TRANSIT, history.get(2).getStatus());
    }

    @Test
    @DisplayName("Verify delivery proof persistence and one-to-one constraint")
    void testDeliveryProofOneToOneConstraint() {
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();

        User customer = userRepository.saveAndFlush(new User("cust3@domain.com", "hash", "Cust 3", "555-333", customerRole));
        User agent = userRepository.saveAndFlush(new User("agent3@domain.com", "hash", "Agent 3", "555-444", agentRole));

        Shipment shipment = shipmentRepository.saveAndFlush(new Shipment(
                "TRK-PROOF-001", customer, "Cust 3", "Addr 1", "555-333",
                "Receiver 3", "Addr 2", "555-555", new BigDecimal("3.200"), new BigDecimal("35.00")
        ));

        DeliveryProof proof1 = new DeliveryProof(shipment, agent, ProofType.OTP, "OTP-VERIFIED-982143", "Receiver 3", "Handed directly to recipient");
        deliveryProofRepository.saveAndFlush(proof1);

        assertTrue(deliveryProofRepository.existsByShipmentId(shipment.getId()));

        // Attempt second proof for the same shipment (must violate unique constraint)
        DeliveryProof proof2 = new DeliveryProof(shipment, agent, ProofType.SIGNATURE, "SIG-BASE64-DATA", "Receiver 3", "Duplicate proof attempt");
        assertThrows(DataIntegrityViolationException.class, () -> {
            deliveryProofRepository.saveAndFlush(proof2);
        }, "Only one delivery proof may exist per shipment");
    }
}
