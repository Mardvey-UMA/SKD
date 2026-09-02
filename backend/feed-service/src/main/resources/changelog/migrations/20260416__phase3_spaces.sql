-- liquibase formatted sql
-- changeset mattew:20260416__create_spaces

CREATE TABLE feed.spaces (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    name        VARCHAR(100) NOT NULL,
    color       VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT  chk_spaces_color CHECK (color IN (
        'RED','ORANGE','YELLOW','GREEN','TEAL','BLUE','PURPLE','PINK'
    )),
    CONSTRAINT  uq_spaces_user_name UNIQUE (user_id, name)
);
CREATE INDEX idx_spaces_user_id ON feed.spaces (user_id);
-- rollback DROP TABLE IF EXISTS feed.spaces;

-- changeset mattew:20260416__create_space_sources

CREATE TABLE feed.space_sources (
    space_id    UUID        NOT NULL REFERENCES feed.spaces(id) ON DELETE CASCADE,
    source_id   UUID        NOT NULL,
    added_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (space_id, source_id)
);
CREATE INDEX idx_space_sources_space ON feed.space_sources (space_id);
-- rollback DROP TABLE IF EXISTS feed.space_sources;

-- changeset mattew:20260416__create_source_additions

CREATE TABLE feed.source_additions (
    user_id     UUID        NOT NULL,
    source_id   UUID        NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_name VARCHAR(255) NOT NULL,
    added_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, source_id)
);
CREATE INDEX idx_source_additions_user ON feed.source_additions (user_id, added_at DESC);
-- rollback DROP TABLE IF EXISTS feed.source_additions;
