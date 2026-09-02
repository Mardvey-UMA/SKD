-- liquibase formatted sql
-- Migration: Unify TIMESTAMPTZ to TIMESTAMP
-- Service: config-service
-- Date: 2026-03-29

-- changeset config:alter-timestamps-to-timestamp splitStatements:true
-- comment: Convert TIMESTAMPTZ columns to TIMESTAMP for consistency with other schemas

ALTER TABLE sources ALTER COLUMN created_at TYPE TIMESTAMP USING created_at AT TIME ZONE 'UTC';
ALTER TABLE sources ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at AT TIME ZONE 'UTC';
-- rollback ALTER TABLE sources ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
-- rollback ALTER TABLE sources ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';
