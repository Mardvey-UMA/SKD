-- liquibase formatted sql
-- changeset feed-service:20260419__create_feed_requests

CREATE TABLE feed.feed_requests (
    request_id        UUID         NOT NULL,
    user_id           UUID         NOT NULL,
    requested_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    page_number       SMALLINT     NOT NULL DEFAULT 0,
    source            VARCHAR(20)  NOT NULL
                          CHECK (source IN ('personalized', 'cold_start', 'cached', 'fallback')),
    count_requested   SMALLINT     NOT NULL DEFAULT 30,
    count_returned    SMALLINT     NOT NULL DEFAULT 0,
    latency_ms        INTEGER,
    latency_breakdown JSONB,
    feature_flags     JSONB,
    ab_bucket         SMALLINT     NOT NULL DEFAULT 0,
    app_version       VARCHAR(32),
    device_type       VARCHAR(32),
    CONSTRAINT pk_feed_requests PRIMARY KEY (request_id)
);

CREATE INDEX idx_feed_requests_user_ts
    ON feed.feed_requests (user_id, requested_at DESC);

CREATE INDEX idx_feed_requests_source_ts
    ON feed.feed_requests (source, requested_at DESC);

-- rollback DROP TABLE IF EXISTS feed.feed_requests CASCADE;

-- changeset feed-service:20260419__create_feed_items

CREATE TABLE feed.feed_items (
    request_id         UUID      NOT NULL,
    position           SMALLINT  NOT NULL,
    content_id         UUID      NOT NULL,
    raw_content_id     UUID,
    final_score        REAL,
    scoring_components JSONB,
    rerank_score       REAL,
    filtered_out_by    VARCHAR(64),
    CONSTRAINT pk_feed_items PRIMARY KEY (request_id, position),
    CONSTRAINT fk_feed_items_request
        FOREIGN KEY (request_id) REFERENCES feed.feed_requests(request_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_feed_items_content_id
    ON feed.feed_items (content_id);

-- rollback DROP TABLE IF EXISTS feed.feed_items CASCADE;
