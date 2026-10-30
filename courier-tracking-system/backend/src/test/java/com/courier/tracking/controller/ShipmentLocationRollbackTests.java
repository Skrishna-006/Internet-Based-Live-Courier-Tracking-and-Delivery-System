package com.courier.tracking.controller;

import com.courier.tracking.dto.LocationUpdateRequest;
import com.courier.tracking.entity.Role;
import com.courier.tracking.entity.RoleName;
import com.courier.tracking.entity.Shipment;
import com.courier.tracking.entity.ShipmentStatus;
import com.courier.tracking.entity.User;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.ShipmentRepository;
import com.courier.tracking.repository.UserRepository;
import com.courier.tracking.service.ShipmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class ShipmentLocationRollbackTests {

    @Autowired
    private ShipmentService shipmentService;

    @MockitoSpyBean
    private ShipmentRepository shipmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void tearDown() {
        shipmentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Transaction rollback: when shipment save fails during location update, state is rolled back")
    void testRollbackWhenShipmentSaveFails() {
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();

        User customer = userRepository.save(new User(
                "loc.rollback.cust@example.com",
                passwordEncoder.encode("Password123!"),
                "Rollback Customer",
                "+1-555-0981",
                customerRole
        ));

        User agent = userRepository.save(new User(
                "loc.rollback.agent@example.com",
                passwordEncoder.encode("Password123!"),
                "Rollback Agent",
                "+1-555-0982",
                agentRole
        ));

        Shipment shipment = new Shipment(
                "TRK-20261029-LOCROLL01",
                customer,
                "Origin Sender",
                "100 Dispatch Blvd",
                "+1-555-1111",
                "Target Recipient",
                "200 Target Ave",
                "+1-555-2222",
                new BigDecimal("2.500"),
                new BigDecimal("22.50"),
                "USD"
        );
        shipment.setStatus(ShipmentStatus.OUT_FOR_DELIVERY);
        shipment.setAssignedAgent(agent);
        final Long shipmentId = shipmentRepository.save(shipment).getId();

        // Simulate database constraint failure on save()
        doThrow(new DataIntegrityViolationException("Simulated database write error"))
                .when(shipmentRepository).save(any(Shipment.class));

        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.2671530"),
                new BigDecimal("-97.7430608"),
                new BigDecimal("10.0"),
                Instant.now()
        );

        // Act & Assert: Exception is thrown
        assertThrows(DataIntegrityViolationException.class, () ->
                shipmentService.updateLiveLocation(shipmentId, request, agent.getEmail())
        );
    }
}
