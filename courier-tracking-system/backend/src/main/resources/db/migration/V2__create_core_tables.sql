-- =============================================================================
-- Migration: V2__create_core_tables.sql
-- Description: Core relational schema for courier tracking and delivery system
-- Domains: Roles, Users, Shipments, Tracking Events, Delivery Proofs
-- =============================================================================

-- 1. Roles table
CREATE TABLE IF NOT EXISTS roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- Seed standard system roles only (No user credentials or accounts are seeded)
INSERT INTO roles (name, description)
SELECT 'ROLE_CUSTOMER', 'Registered parcel sender or recipient'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_CUSTOMER');

INSERT INTO roles (name, description)
SELECT 'ROLE_DELIVERY_AGENT', 'Courier delivery personnel and field driver'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_DELIVERY_AGENT');

INSERT INTO roles (name, description)
SELECT 'ROLE_ADMIN', 'Logistics operations manager and administrator'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_ADMIN');

-- 2. Users table (Stores normalized email, hashed passwords, and assigned role)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    phone_number VARCHAR(30),
    role_id INTEGER NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role_id ON users(role_id);

-- 3. Shipments table (Lifecycle state machine, consignor/consignee, agent assignment)
CREATE TABLE IF NOT EXISTS shipments (
    id BIGSERIAL PRIMARY KEY,
    tracking_number VARCHAR(32) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    assigned_agent_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'CREATED',
    sender_name VARCHAR(120) NOT NULL,
    sender_address TEXT NOT NULL,
    sender_phone VARCHAR(30) NOT NULL,
    recipient_name VARCHAR(120) NOT NULL,
    recipient_address TEXT NOT NULL,
    recipient_phone VARCHAR(30) NOT NULL,
    recipient_latitude DECIMAL(10, 7),
    recipient_longitude DECIMAL(10, 7),
    weight_kg DECIMAL(8, 3) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    estimated_delivery_at TIMESTAMP WITH TIME ZONE,
    actual_delivery_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_shipments_tracking_number ON shipments(tracking_number);
CREATE INDEX IF NOT EXISTS idx_shipments_status ON shipments(status);
CREATE INDEX IF NOT EXISTS idx_shipments_customer_id ON shipments(customer_id);
CREATE INDEX IF NOT EXISTS idx_shipments_assigned_agent_id ON shipments(assigned_agent_id);
CREATE INDEX IF NOT EXISTS idx_shipments_created_at ON shipments(created_at);

-- 4. Tracking Events table (Historical immutable audit log of transitions & telemetry)
-- ON DELETE RESTRICT: Preserves audit integrity; shipments with history cannot be deleted
CREATE TABLE IF NOT EXISTS tracking_events (
    id BIGSERIAL PRIMARY KEY,
    shipment_id BIGINT NOT NULL REFERENCES shipments(id) ON DELETE RESTRICT,
    status VARCHAR(40) NOT NULL,
    latitude DECIMAL(10, 7),
    longitude DECIMAL(10, 7),
    location_name VARCHAR(255),
    remarks TEXT,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tracking_events_shipment_id ON tracking_events(shipment_id);
CREATE INDEX IF NOT EXISTS idx_tracking_events_timestamp ON tracking_events(event_timestamp);

-- 5. Delivery Proofs table (Proof of delivery: OTP, signature, photo)
-- ON DELETE RESTRICT: Enforces legal compliance and prevents deletion of delivery verification
CREATE TABLE IF NOT EXISTS delivery_proofs (
    id BIGSERIAL PRIMARY KEY,
    shipment_id BIGINT NOT NULL UNIQUE REFERENCES shipments(id) ON DELETE RESTRICT,
    captured_by_agent_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    proof_type VARCHAR(30) NOT NULL,
    proof_value TEXT NOT NULL,
    recipient_name VARCHAR(120),
    verification_notes VARCHAR(500),
    verified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_delivery_proofs_shipment_id ON delivery_proofs(shipment_id);
CREATE INDEX IF NOT EXISTS idx_delivery_proofs_agent_id ON delivery_proofs(captured_by_agent_id);
