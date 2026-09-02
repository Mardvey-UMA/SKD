CREATE SCHEMA IF NOT EXISTS data_flow;

CREATE TABLE IF NOT EXISTS data_flow.published_content (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    content_id          UUID            NOT NULL,
    external_id         VARCHAR(255)    NOT NULL,
    title               VARCHAR(500),
    description         TEXT,
    content             TEXT,
    content_format      VARCHAR(20)     NOT NULL DEFAULT 'HTML',
    source_id           UUID            NOT NULL,
    source_type         VARCHAR(50)     NOT NULL,
    source_subtype      VARCHAR(50),
    url                 VARCHAR(2048),
    published_at        TIMESTAMPTZ,
    author_id           VARCHAR(255),
    author_name         VARCHAR(255),
    media               TEXT,
    metadata            TEXT,
    dedup_article_id    BIGINT,
    content_hash        VARCHAR(64),
    content_html        TEXT,
    content_text        TEXT,
    preview_text        TEXT,
    content_text_length INTEGER,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT uk_published_source_external UNIQUE (source_type, external_id)
);

CREATE TABLE IF NOT EXISTS data_flow.articles (
    id              BIGSERIAL       PRIMARY KEY,
    raw_content_id  UUID            NOT NULL UNIQUE,
    content_hash    TEXT,
    normalized_text TEXT,
    source          TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS data_flow.similarities (
    article_a   BIGINT      NOT NULL,
    article_b   BIGINT      NOT NULL,
    score       REAL        NOT NULL,
    rel_type    TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (article_a, article_b),
    CONSTRAINT chk_article_order CHECK (article_a < article_b)
);
