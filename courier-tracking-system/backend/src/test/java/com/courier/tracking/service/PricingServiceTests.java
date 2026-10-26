package com.courier.tracking.service;

import com.courier.tracking.config.PricingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PricingServiceTests {

    private PricingProperties pricingProperties;
    private ShipmentService shipmentService;

    @BeforeEach
    void setUp() {
        pricingProperties = new PricingProperties();
        pricingProperties.setBaseRate(new BigDecimal("10.00"));
        pricingProperties.setPerKgRate(new BigDecimal("5.00"));
        pricingProperties.setCurrency("USD");
        pricingProperties.setMinPrice(new BigDecimal("10.00"));

        // ShipmentService calculatePrice only interacts with PricingProperties; repositories are not invoked
        shipmentService = new ShipmentService(null, null, pricingProperties);
    }

    @Test
    @DisplayName("Verify price calculation with default rates ($10 base + $5/kg)")
    void testCalculatePriceWithDefaultRates() {
        // 2.5 kg -> 10.00 + (2.5 * 5.00) = 22.50
        BigDecimal price = shipmentService.calculatePrice(new BigDecimal("2.500"));
        assertEquals(new BigDecimal("22.50"), price);

        // 0.5 kg -> 10.00 + (0.5 * 5.00) = 12.50
        BigDecimal priceSmall = shipmentService.calculatePrice(new BigDecimal("0.500"));
        assertEquals(new BigDecimal("12.50"), priceSmall);
    }

    @Test
    @DisplayName("Verify price calculation with custom configurable rates")
    void testCalculatePriceWithCustomRates() {
        pricingProperties.setBaseRate(new BigDecimal("15.50"));
        pricingProperties.setPerKgRate(new BigDecimal("7.25"));
        pricingProperties.setCurrency("EUR");

        // 3.0 kg -> 15.50 + (3.0 * 7.25) = 15.50 + 21.75 = 37.25
        BigDecimal price = shipmentService.calculatePrice(new BigDecimal("3.000"));
        assertEquals(new BigDecimal("37.25"), price);
        assertEquals("EUR", pricingProperties.getCurrency());
    }

    @Test
    @DisplayName("Verify minimum price floor enforcement")
    void testMinimumPriceFloor() {
        pricingProperties.setBaseRate(new BigDecimal("2.00"));
        pricingProperties.setPerKgRate(new BigDecimal("1.00"));
        pricingProperties.setMinPrice(new BigDecimal("12.00"));

        // 1.0 kg -> 2.00 + 1.00 = 3.00, which is below minPrice (12.00) -> returns 12.00
        BigDecimal price = shipmentService.calculatePrice(new BigDecimal("1.000"));
        assertEquals(new BigDecimal("12.00"), price);
    }

    @Test
    @DisplayName("Verify null weight input throws IllegalArgumentException")
    void testNullWeightThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> shipmentService.calculatePrice(null));
    }
}
