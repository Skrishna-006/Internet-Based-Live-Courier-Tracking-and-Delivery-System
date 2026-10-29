package com.courier.tracking.controller;

import com.courier.tracking.dto.VerifyDeliveryOtpRequest;
import com.courier.tracking.entity.*;
import com.courier.tracking.repository.DeliveryProofRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class ShipmentDeliveryRollbackTests {

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

    @Autowired
    private DeliveryProofRepository deliveryProofRepository;

    @AfterEach
    void tearDown() {
        deliveryProofRepository.deleteAll();
        trackingEventRepository.deleteAll();
        shipmentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Transaction rollback: when tracking event save fails during delivery confirmation, status remains OUT_FOR_DELIVERY")
    void testRollbackWhenTrackingEventSaveFails() {
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();

        User customer = userRepository.save(new User(
                "rollback.cust@example.com",
                passwordEncoder.encode("Password123!"),
                "Rollback Customer",
                "+1-555-0951",
                customerRole
        ));

        User agent = userRepository.save(new User(
                "rollback.agent2@example.com",
                passwordEncoder.encode("Password123!"),
                "Rollback Agent",
                "+1-555-0952",
                agentRole
        ));

        String rawOtp = "654321";
        Shipment shipment = new Shipment(
                "TRK-20261029-ROLLBACK02",
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
        shipment.setDeliveryOtpHash(passwordEncoder.encode(rawOtp));
        shipment.setDeliveryOtpExpiresAt(Instant.now().plusSeconds(600));
        shipment.setDeliveryOtpAttempts(0);
        final Long shipmentId = shipmentRepository.save(shipment).getId();

        // Simulate failure during tracking event save
        doThrow(new DataIntegrityViolationException("Simulated tracking event constraint violation"))
                .when(trackingEventRepository).save(any(TrackingEvent.class));

        VerifyDeliveryOtpRequest request = new VerifyDeliveryOtpRequest(rawOtp, "Target Recipient");

        // Act & Assert: Exception is thrown
        assertThrows(DataIntegrityViolationException.class, () ->
                shipmentService.confirmDeliveryWithOtp(shipmentId, request, agent.getEmail())
        );

        // Verify transaction rolled back: status remains OUT_FOR_DELIVERY and OTP hash is intact
        Shipment reloaded = shipmentRepository.findById(shipmentId).orElseThrow();
        assertEquals(ShipmentStatus.OUT_FOR_DELIVERY, reloaded.getStatus());
        assertNotNull(reloaded.getDeliveryOtpHash());
        assertEquals(0, deliveryProofRepository.count());
    }
}
