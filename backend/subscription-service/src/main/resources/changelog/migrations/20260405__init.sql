-- liquibase formatted sql
-- changeset mattew:20260405__create_plans

CREATE TABLE plans (
    id              VARCHAR(50) PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    price_kopecks   INTEGER NOT NULL,
    duration_days   INTEGER NOT NULL,
    active          BOOLEAN DEFAULT TRUE
);
-- rollback DROP TABLE IF EXISTS plans;

-- changeset mattew:20260405__seed_plans

INSERT INTO plans (id, name, price_kopecks, duration_days, active) VALUES
    ('premium_monthly', 'Premium (month)', 29900, 30, TRUE),
    ('premium_yearly', 'Premium (year)', 299000, 365, TRUE);
-- rollback DELETE FROM plans WHERE id IN ('premium_monthly', 'premium_yearly');

-- changeset mattew:20260405__create_subscriptions

CREATE TABLE subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID UNIQUE NOT NULL,
    plan_id         VARCHAR(50) NOT NULL REFERENCES plans(id),
    tier            VARCHAR(20) NOT NULL DEFAULT 'premium',
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    auto_renew      BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_sub_user ON subscriptions (user_id);
CREATE INDEX idx_sub_expires ON subscriptions (expires_at) WHERE status = 'active';
-- rollback DROP TABLE IF EXISTS subscriptions;

-- changeset mattew:20260405__create_payments

CREATE TABLE payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    plan_id         VARCHAR(50) NOT NULL REFERENCES plans(id),
    amount_kopecks  INTEGER NOT NULL,
    currency        VARCHAR(3) DEFAULT 'RUB',
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    external_id     VARCHAR(255),
    external_status VARCHAR(50),
    confirmation_url VARCHAR(500),
    idempotency_key UUID UNIQUE NOT NULL,
    metadata        TEXT,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_pay_user ON payments (user_id, created_at DESC);
CREATE INDEX idx_pay_pending ON payments (user_id, plan_id, created_at DESC) WHERE status = 'pending';
CREATE INDEX idx_pay_external ON payments (external_id);
-- rollback DROP TABLE IF EXISTS payments;

-- changeset mattew:20260405__create_saved_payment_methods

CREATE TABLE saved_payment_methods (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID UNIQUE NOT NULL,
    yookassa_method_id  VARCHAR(255) NOT NULL,
    type                VARCHAR(50) NOT NULL,
    card_last4          VARCHAR(4),
    active              BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMPTZ DEFAULT now(),
    updated_at          TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_spm_user ON saved_payment_methods (user_id) WHERE active = TRUE;
-- rollback DROP TABLE IF EXISTS saved_payment_methods;

-- changeset mattew:20260405__create_webhook_log

CREATE TABLE webhook_log (
    external_event_id VARCHAR(255) PRIMARY KEY,
    event_type        VARCHAR(50),
    received_at       TIMESTAMPTZ DEFAULT now(),
    processed         BOOLEAN DEFAULT FALSE,
    payload           TEXT
);
-- rollback DROP TABLE IF EXISTS webhook_log;

-- changeset mattew:20260405__create_outbox

CREATE TABLE outbox (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         TEXT NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
-- rollback DROP TABLE IF EXISTS outbox;
