--liquibase formatted sql

--changeset integration:create-published-content-table
CREATE TABLE published_content (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    content_id          UUID            NOT NULL REFERENCES data_flow.raw_content(id) ON DELETE CASCADE,
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
    media               JSONB,
    metadata            JSONB,
    dedup_article_id    BIGINT,
    dedup_status        VARCHAR(20)     NOT NULL DEFAULT 'NEW',
    content_hash        VARCHAR(64),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uk_published_source_external UNIQUE (source_type, external_id)
);

--changeset integration:create-published-content-indexes
CREATE INDEX idx_published_published_at ON published_content (published_at DESC NULLS LAST);
CREATE INDEX idx_published_created_at ON published_content (created_at DESC);
CREATE INDEX idx_published_source_type ON published_content (source_type);
CREATE INDEX idx_published_source_type_date ON published_content (source_type, published_at DESC NULLS LAST);
CREATE INDEX idx_published_source_id ON published_content (source_id);
CREATE INDEX idx_published_content_id ON published_content (content_id);

--changeset integration:create-published-content-updated-at-trigger splitStatements:false
CREATE OR REPLACE FUNCTION update_published_content_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_published_content_updated_at
    BEFORE UPDATE ON published_content
    FOR EACH ROW EXECUTE FUNCTION update_published_content_updated_at();
