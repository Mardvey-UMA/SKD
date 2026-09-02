-- liquibase formatted sql
-- changeset mattew:20260405__create_profiles

CREATE TABLE profiles (
    id                   UUID         NOT NULL,
    email                VARCHAR(255) NOT NULL,
    display_name         VARCHAR(255),
    avatar_url           VARCHAR(1024),
    subscription_tier    VARCHAR(50)  NOT NULL DEFAULT 'free',
    onboarding_completed BOOLEAN      NOT NULL DEFAULT false,
    categories           TEXT,
    created_at           TIMESTAMPTZ  DEFAULT now(),
    updated_at           TIMESTAMPTZ  DEFAULT now(),
    CONSTRAINT pk_profiles PRIMARY KEY (id)
);
-- rollback DROP TABLE IF EXISTS profiles;

-- changeset mattew:20260405__create_outbox

CREATE TABLE outbox (
    id             BIGSERIAL    NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  DEFAULT now(),
    published_at   TIMESTAMPTZ,
    CONSTRAINT pk_outbox PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
-- rollback DROP TABLE IF EXISTS outbox;
