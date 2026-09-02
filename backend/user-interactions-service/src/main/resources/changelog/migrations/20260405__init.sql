-- liquibase formatted sql
-- changeset mattew:20260405__create_user_interactions splitStatements:true

CREATE TABLE user_interactions (
    id          BIGSERIAL,
    user_id     UUID          NOT NULL,
    content_id  UUID          NOT NULL,
    action_type VARCHAR(50)   NOT NULL,
    duration_sec INT,
    client_ts   TIMESTAMPTZ   NOT NULL,
    server_ts   TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (server_ts, id)
) PARTITION BY RANGE (server_ts);
-- rollback DROP TABLE IF EXISTS user_interactions;

-- changeset mattew:20260405__create_partitions splitStatements:true

CREATE TABLE user_interactions_2026_04 PARTITION OF user_interactions
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');

CREATE TABLE user_interactions_2026_05 PARTITION OF user_interactions
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE user_interactions_default PARTITION OF user_interactions DEFAULT;
-- rollback DROP TABLE IF EXISTS user_interactions_default; DROP TABLE IF EXISTS user_interactions_2026_05; DROP TABLE IF EXISTS user_interactions_2026_04;

-- changeset mattew:20260405__create_interactions_indexes

CREATE INDEX idx_interactions_user_ts ON user_interactions (user_id, server_ts);
CREATE INDEX idx_interactions_content_action ON user_interactions (content_id, action_type);
CREATE INDEX idx_interactions_action_ts ON user_interactions (action_type, server_ts);
-- rollback DROP INDEX IF EXISTS idx_interactions_action_ts; DROP INDEX IF EXISTS idx_interactions_content_action; DROP INDEX IF EXISTS idx_interactions_user_ts;
