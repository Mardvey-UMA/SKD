-- liquibase formatted sql
-- changeset mattew:20260421__create_blocked_sources

CREATE TABLE feed.blocked_sources (
    user_id    UUID        NOT NULL,
    source_id  UUID        NOT NULL,
    blocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, source_id)
);
CREATE INDEX idx_blocked_sources_user_ts ON feed.blocked_sources (user_id, blocked_at DESC);
-- rollback DROP TABLE IF EXISTS feed.blocked_sources;
