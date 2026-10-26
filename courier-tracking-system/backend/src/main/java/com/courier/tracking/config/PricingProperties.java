package com.courier.tracking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Configuration properties for shipment pricing rules.
 * Supports environment variable overrides:
 * - APP_PRICING_BASE_RATE / app.pricing.base-rate
 * - APP_PRICING_PER_KG_RATE / app.pricing.per-kg-rate
 * - APP_PRICING_CURRENCY / app.pricing.currency
 * - APP_PRICING_MIN_PRICE / app.pricing.min-price
 */
@Configuration
@ConfigurationProperties(prefix = "app.pricing")
public class PricingProperties {

    /**
     * Base flat rate applied to all shipments (default: 10.00).
     */
    private BigDecimal baseRate = new BigDecimal("10.00");

    /**
     * Variable rate charged per kilogram (default: 5.00).
     */
    private BigDecimal perKgRate = new BigDecimal("5.00");

    /**
     * Standard currency code for pricing (default: USD).
     */
    private String currency = "USD";

    /**
     * Minimum price floor for any shipment (default: 10.00).
     */
    private BigDecimal minPrice = new BigDecimal("10.00");

    public BigDecimal getBaseRate() {
        return baseRate;
    }

    public void setBaseRate(BigDecimal baseRate) {
        this.baseRate = baseRate;
    }

    public BigDecimal getPerKgRate() {
        return perKgRate;
    }

    public void setPerKgRate(BigDecimal perKgRate) {
        this.perKgRate = perKgRate;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }
}
