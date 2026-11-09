-- =============================================================================
-- Migration: V6__add_customer_delivery_pin_to_shipments.sql
-- Description: Add customer delivery PIN column for secure demonstration presentation
-- =============================================================================

ALTER TABLE shipments ADD COLUMN IF NOT EXISTS customer_delivery_pin VARCHAR(10);
