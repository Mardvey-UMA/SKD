-- liquibase formatted sql
-- changeset mattew:20260420__add_scroll_depth_and_metadata splitStatements:true

ALTER TABLE interactions.user_interactions
    ADD COLUMN IF NOT EXISTS scroll_depth REAL,
    ADD COLUMN IF NOT EXISTS metadata JSONB;

COMMENT ON COLUMN interactions.user_interactions.scroll_depth IS
    '0.0-1.0 fraction, populated for CLOSE events to drive close_full / close_half classification';
COMMENT ON COLUMN interactions.user_interactions.metadata IS
    'Forward-compat JSONB for client-specific fields (device, app_version extras, etc.)';
-- rollback ALTER TABLE interactions.user_interactions DROP COLUMN IF EXISTS scroll_depth, DROP COLUMN IF EXISTS metadata;
