-- =============================================================================
-- Migration: V1__init_schema.sql
-- Description: Phase 1 baseline migration marker for Flyway schema versioning
-- Application: Internet-Based Live Courier Tracking and Delivery System
-- =============================================================================

CREATE TABLE IF NOT EXISTS system_metadata (
    id SERIAL PRIMARY KEY,
    key_name VARCHAR(100) NOT NULL UNIQUE,
    value_data VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_metadata (key_name, value_data)
SELECT 'system_name', 'Internet-Based Live Courier Tracking and Delivery System'
WHERE NOT EXISTS (SELECT 1 FROM system_metadata WHERE key_name = 'system_name');

INSERT INTO system_metadata (key_name, value_data)
SELECT 'phase', '1 - Architecture and Project Foundation'
WHERE NOT EXISTS (SELECT 1 FROM system_metadata WHERE key_name = 'phase');

INSERT INTO system_metadata (key_name, value_data)
SELECT 'version', '1.0.0'
WHERE NOT EXISTS (SELECT 1 FROM system_metadata WHERE key_name = 'version');
