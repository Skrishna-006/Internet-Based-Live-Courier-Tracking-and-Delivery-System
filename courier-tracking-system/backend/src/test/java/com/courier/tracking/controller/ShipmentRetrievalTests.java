package com.courier.tracking.controller;

import com.courier.tracking.entity.Role;
import com.courier.tracking.entity.RoleName;
import com.courier.tracking.entity.Shipment;
import com.courier.tracking.entity.ShipmentStatus;
import com.courier.tracking.entity.User;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.ShipmentRepository;
import com.courier.tracking.repository.UserRepository;
import com.courier.tracking.security.JwtTokenProvider;
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

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShipmentRetrievalTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User customerUser;
    private User otherCustomer;
    private User agentUser;
    private User adminUser;
    private User disabledCustomer;
    private String customerToken;
    private String otherCustomerToken;
    private String agentToken;
    private String adminToken;
    private String disabledCustomerToken;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();

        customerUser = userRepository.save(new User(
                "retrieval.customer@example.com",
                passwordEncoder.encode("Password123!"),
                "Primary Consignor",
                "+1-555-0801",
                customerRole
        ));

        otherCustomer = userRepository.save(new User(
                "other.retrieval@example.com",
                passwordEncoder.encode("Password123!"),
                "Secondary Consignor",
                "+1-555-0802",
                customerRole
        ));

        agentUser = userRepository.save(new User(
                "retrieval.agent@example.com",
                passwordEncoder.encode("Password123!"),
                "Agent Courier",
                "+1-555-0803",
                agentRole
        ));

        adminUser = userRepository.save(new User(
                "retrieval.admin@example.com",
                passwordEncoder.encode("Password123!"),
                "Admin Supervisor",
                "+1-555-0804",
                adminRole
        ));

        disabledCustomer = new User(
                "disabled.customer@example.com",
                passwordEncoder.encode("Password123!"),
                "Disabled Consignor",
                "+1-555-0805",
                customerRole
        );
        disabledCustomer.setActive(false);
        disabledCustomer = userRepository.save(disabledCustomer);

        customerToken = jwtTokenProvider.generateToken(customerUser);
        otherCustomerToken = jwtTokenProvider.generateToken(otherCustomer);
        agentToken = jwtTokenProvider.generateToken(agentUser);
        adminToken = jwtTokenProvider.generateToken(adminUser);
        disabledCustomerToken = jwtTokenProvider.generateToken(disabledCustomer);
    }

    private Shipment createShipment(User owner, String suffix, BigDecimal weight, BigDecimal price) {
        Shipment shipment = new Shipment(
                "TRK-TEST-" + suffix,
                owner,
                "Sender " + suffix,
                "Origin Address " + suffix,
                "+1-555-1111",
                "Recipient " + suffix,
                "Destination Address " + suffix,
                "+1-555-2222",
                weight,
                price,
                "USD"
        );
        shipment.setStatus(ShipmentStatus.CREATED);
        shipment.setEstimatedDeliveryAt(Instant.now().plusSeconds(86400));
        return shipmentRepository.save(shipment);
    }

    @Test
    @DisplayName("1. Authenticated customer retrieves their own shipments list")
    void testAuthenticatedCustomerRetrievesOwnShipmentsList() throws Exception {
        createShipment(customerUser, "001", new BigDecimal("1.500"), new BigDecimal("17.50"));
        createShipment(customerUser, "002", new BigDecimal("3.000"), new BigDecimal("25.00"));

        mockMvc.perform(get("/api/v1/shipments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Shipments retrieved successfully"))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].customerId").value(customerUser.getId()))
                .andExpect(jsonPath("$.data.content[1].customerId").value(customerUser.getId()));
    }

    @Test
    @DisplayName("2. Authenticated customer retrieves their own shipment by ID")
    void testAuthenticatedCustomerRetrievesOwnShipmentById() throws Exception {
        Shipment shipment = createShipment(customerUser, "SINGLE", new BigDecimal("2.000"), new BigDecimal("20.00"));

        mockMvc.perform(get("/api/v1/shipments/{id}", shipment.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Shipment retrieved successfully"))
                .andExpect(jsonPath("$.data.id").value(shipment.getId()))
                .andExpect(jsonPath("$.data.trackingNumber").value("TRK-TEST-SINGLE"))
                .andExpect(jsonPath("$.data.customerId").value(customerUser.getId()))
                .andExpect(jsonPath("$.data.customerEmail").value(customerUser.getEmail()))
                .andExpect(jsonPath("$.data.price").value(20.00))
                .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    @DisplayName("3. Customer cannot retrieve another customer's shipment by ID (403 Forbidden)")
    void testCustomerCannotRetrieveAnotherCustomersShipment() throws Exception {
        Shipment otherShipment = createShipment(otherCustomer, "OTHER", new BigDecimal("4.000"), new BigDecimal("30.00"));

        mockMvc.perform(get("/api/v1/shipments/{id}", otherShipment.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: You do not have permission to perform this action"));
    }

    @Test
    @DisplayName("4. Retrieving a non-existent shipment returns 404 Not Found")
    void testNonExistentShipmentReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/shipments/{id}", 999999L)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Shipment not found with id: '999999'"));
    }

    @Test
    @DisplayName("5. Customer shipment list contains only their own shipments (isolation)")
    void testCustomerShipmentListContainsOnlyOwnShipments() throws Exception {
        createShipment(customerUser, "OWN-1", new BigDecimal("1.000"), new BigDecimal("15.00"));
        createShipment(customerUser, "OWN-2", new BigDecimal("2.000"), new BigDecimal("20.00"));
        createShipment(otherCustomer, "OTHER-1", new BigDecimal("5.000"), new BigDecimal("35.00"));

        // Customer's list should only have 2 shipments, both belonging to customerUser
        mockMvc.perform(get("/api/v1/shipments")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].customerId").value(customerUser.getId()))
                .andExpect(jsonPath("$.data.content[1].customerId").value(customerUser.getId()));

        // Other customer's list should only have 1 shipment
        mockMvc.perform(get("/api/v1/shipments")
                        .header("Authorization", "Bearer " + otherCustomerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].customerId").value(otherCustomer.getId()));
    }

    @Test
    @DisplayName("6. Unauthenticated requests to list or retrieve by ID are rejected with 401")
    void testUnauthenticatedRequestsRejected() throws Exception {
        // List without token
        mockMvc.perform(get("/api/v1/shipments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // List with invalid token
        mockMvc.perform(get("/api/v1/shipments")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // Get by ID without token
        mockMvc.perform(get("/api/v1/shipments/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // Get by ID with invalid token
        mockMvc.perform(get("/api/v1/shipments/1")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("7. Empty shipment list returns 200 OK with empty content array")
    void testEmptyShipmentListReturnsCorrectResponse() throws Exception {
        mockMvc.perform(get("/api/v1/shipments")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Shipments retrieved successfully"))
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.empty").value(true));
    }

    @Test
    @DisplayName("8. Pagination parameters work correctly across multiple pages")
    void testPaginationWorksCorrectly() throws Exception {
        for (int i = 1; i <= 5; i++) {
            createShipment(customerUser, String.format("PAGE-%03d", i),
                    new BigDecimal(i), new BigDecimal(10 + i * 5));
        }

        // Page 0, size 2
        mockMvc.perform(get("/api/v1/shipments")
                        .header("Authorization", "Bearer " + customerToken)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false));

        // Page 1, size 2
        mockMvc.perform(get("/api/v1/shipments")
                        .header("Authorization", "Bearer " + customerToken)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").value(1))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.first").value(false))
                .andExpect(jsonPath("$.data.last").value(false));

        // Page 2, size 2 (last page, single element)
        mockMvc.perform(get("/api/v1/shipments")
                        .header("Authorization", "Bearer " + customerToken)
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.first").value(false))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    @DisplayName("9. Delivery-agent role receives 403 Forbidden on customer shipment endpoints")
    void testDeliveryAgentCannotAccessCustomerShipments() throws Exception {
        Shipment shipment = createShipment(customerUser, "AGENT-TEST", new BigDecimal("1.000"), new BigDecimal("15.00"));

        // List
        mockMvc.perform(get("/api/v1/shipments")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: You do not have permission to perform this action"));

        // Get by ID
        mockMvc.perform(get("/api/v1/shipments/{id}", shipment.getId())
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: You do not have permission to perform this action"));
    }

    @Test
    @DisplayName("10. Admin role receives 403 Forbidden on customer-specific retrieval endpoints")
    void testAdminCannotAccessCustomerShipments() throws Exception {
        Shipment shipment = createShipment(customerUser, "ADMIN-TEST", new BigDecimal("1.000"), new BigDecimal("15.00"));

        // List
        mockMvc.perform(get("/api/v1/shipments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: You do not have permission to perform this action"));

        // Get by ID
        mockMvc.perform(get("/api/v1/shipments/{id}", shipment.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: You do not have permission to perform this action"));
    }

    @Test
    @DisplayName("11. Deactivated customer account is rejected with 401 Unauthorized")
    void testDeactivatedCustomerCannotAccessShipments() throws Exception {
        mockMvc.perform(get("/api/v1/shipments")
                        .header("Authorization", "Bearer " + disabledCustomerToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/shipments/1")
                        .header("Authorization", "Bearer " + disabledCustomerToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
