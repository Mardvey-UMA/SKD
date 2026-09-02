-- liquibase formatted sql

-- changeset mattew:20260419__extend_user_interactions splitStatements:true
ALTER TABLE user_interactions ADD COLUMN IF NOT EXISTS feed_request_id UUID;
ALTER TABLE user_interactions ADD COLUMN IF NOT EXISTS position_in_feed SMALLINT;
ALTER TABLE user_interactions ADD COLUMN IF NOT EXISTS device_type VARCHAR(32);
ALTER TABLE user_interactions ADD COLUMN IF NOT EXISTS app_version VARCHAR(32);
ALTER TABLE user_interactions ADD COLUMN IF NOT EXISTS ab_bucket SMALLINT DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_ui_feed_request ON user_interactions (feed_request_id) WHERE feed_request_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ui_user_time ON user_interactions (user_id, server_ts DESC);
-- rollback ALTER TABLE user_interactions DROP COLUMN IF EXISTS feed_request_id;
-- rollback ALTER TABLE user_interactions DROP COLUMN IF EXISTS position_in_feed;
-- rollback ALTER TABLE user_interactions DROP COLUMN IF EXISTS device_type;
-- rollback ALTER TABLE user_interactions DROP COLUMN IF EXISTS app_version;
-- rollback ALTER TABLE user_interactions DROP COLUMN IF EXISTS ab_bucket;
-- rollback DROP INDEX IF EXISTS idx_ui_feed_request;
-- rollback DROP INDEX IF EXISTS idx_ui_user_time;
