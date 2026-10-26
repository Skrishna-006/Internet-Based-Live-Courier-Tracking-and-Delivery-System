-- =============================================================================
-- Migration: V3__add_currency_to_shipments.sql
-- Description: Add currency column to shipments table for configurable pricing
-- =============================================================================

ALTER TABLE shipments ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'USD';
