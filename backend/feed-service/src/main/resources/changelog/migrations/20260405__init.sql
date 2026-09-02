-- liquibase formatted sql
-- changeset mattew:20260405__create_feed_schema

CREATE SCHEMA IF NOT EXISTS feed;
-- rollback DROP SCHEMA IF EXISTS feed;

-- changeset mattew:20260405__create_user_bookmarks

CREATE TABLE feed.user_bookmarks (
    user_id     UUID        NOT NULL,
    content_id  UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, content_id)
);
CREATE INDEX idx_bookmarks_user_ts ON feed.user_bookmarks (user_id, created_at DESC);
-- rollback DROP TABLE IF EXISTS feed.user_bookmarks;

-- changeset mattew:20260405__create_user_likes

CREATE TABLE feed.user_likes (
    user_id     UUID        NOT NULL,
    content_id  UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, content_id)
);
CREATE INDEX idx_likes_user_ts ON feed.user_likes (user_id, created_at DESC);
-- rollback DROP TABLE IF EXISTS feed.user_likes;

-- changeset mattew:20260405__create_user_dislikes

CREATE TABLE feed.user_dislikes (
    user_id     UUID        NOT NULL,
    content_id  UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, content_id)
);
CREATE INDEX idx_dislikes_user_ts ON feed.user_dislikes (user_id, created_at DESC);
-- rollback DROP TABLE IF EXISTS feed.user_dislikes;
