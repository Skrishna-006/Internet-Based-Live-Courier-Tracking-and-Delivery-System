-- =============================================================================
-- Migration: V4__add_delivery_otp_to_shipments.sql
-- Description: Add delivery OTP security and verification fields to shipments table
-- =============================================================================

ALTER TABLE shipments ADD COLUMN IF NOT EXISTS delivery_otp_hash VARCHAR(255);
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS delivery_otp_expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS delivery_otp_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS delivery_otp_last_sent_at TIMESTAMP WITH TIME ZONE;
