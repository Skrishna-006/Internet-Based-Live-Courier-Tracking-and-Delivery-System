package com.courier.tracking.controller;

import com.courier.tracking.dto.UpdateShipmentStatusRequest;
import com.courier.tracking.entity.*;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.ShipmentRepository;
import com.courier.tracking.repository.TrackingEventRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class ShipmentStatusRollbackTests {

    @Autowired
    private ShipmentService shipmentService;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private TrackingEventRepository trackingEventRepository;

    @AfterEach
    void tearDown() {
        shipmentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Transaction rollback: when tracking event save fails, shipment status change is rolled back")
    void testRollbackWhenTrackingEventFails() {
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();

        User admin = userRepository.save(new User(
                "rollback.admin@example.com",
                passwordEncoder.encode("Password123!"),
                "Rollback Admin",
                "+1-555-0901",
                adminRole
        ));

        User customer = userRepository.save(new User(
                "rollback.customer@example.com",
                passwordEncoder.encode("Password123!"),
                "Rollback Customer",
                "+1-555-0902",
                customerRole
        ));

        User agent = userRepository.save(new User(
                "rollback.agent@example.com",
                passwordEncoder.encode("Password123!"),
                "Rollback Agent",
                "+1-555-0903",
                agentRole
        ));

        Shipment shipment = new Shipment(
                "TRK-20261029-ROLLBACK01",
                customer,
                "Origin",
                "123 Street",
                "+1-555-1111",
                "Dest",
                "456 Street",
                "+1-555-2222",
                new BigDecimal("2.000"),
                new BigDecimal("20.00"),
                "USD"
        );
        shipment.setStatus(ShipmentStatus.ASSIGNED);
        shipment.setAssignedAgent(agent);
        shipment.setEstimatedDeliveryAt(Instant.now().plusSeconds(86400));
        final Long shipmentId = shipmentRepository.save(shipment).getId();

        // Force trackingEventRepository to throw an exception on save()
        doThrow(new DataIntegrityViolationException("Simulated tracking event constraint violation"))
                .when(trackingEventRepository).save(any(TrackingEvent.class));

        UpdateShipmentStatusRequest request = new UpdateShipmentStatusRequest(ShipmentStatus.PICKED_UP);

        // Act & Assert: Exception is thrown
        assertThrows(DataIntegrityViolationException.class, () ->
                shipmentService.updateShipmentStatus(shipmentId, request, admin.getEmail())
        );

        // Verify that shipment status remains ASSIGNED in the database
        Shipment reloaded = shipmentRepository.findById(shipmentId).orElseThrow();
        assertEquals(ShipmentStatus.ASSIGNED, reloaded.getStatus());
    }
}
