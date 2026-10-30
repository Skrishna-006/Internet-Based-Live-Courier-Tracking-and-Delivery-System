-- =============================================================================
-- Migration: V5__add_live_location_to_shipments.sql
-- Description: Add current live telemetry location fields to shipments table
-- =============================================================================

ALTER TABLE shipments ADD COLUMN IF NOT EXISTS current_latitude DECIMAL(10, 7);
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS current_longitude DECIMAL(10, 7);
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS last_location_updated_at TIMESTAMP WITH TIME ZONE;
