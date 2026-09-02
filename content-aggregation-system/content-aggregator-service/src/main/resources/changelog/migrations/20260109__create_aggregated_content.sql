-- liquibase formatted sql
-- Migration: aggregated_content table
-- Service: content-aggregator-service
-- Date: 2026-01-09

-- changeset aggregator:create-aggregated-content splitStatements:true
-- comment: Create aggregated_content table with indexes

CREATE TABLE IF NOT EXISTS aggregated_content (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id VARCHAR(255) NOT NULL,
    title VARCHAR(500),
    description TEXT,
    content TEXT,
    content_format VARCHAR(20) NOT NULL DEFAULT 'HTML',
    source_id VARCHAR(255) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    url VARCHAR(2048),
    published_at TIMESTAMP NOT NULL,
    author VARCHAR(255),
    media JSONB,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP DEFAULT NOW() NOT NULL,

    CONSTRAINT uk_aggregated_content_source_external UNIQUE (source_type, external_id)
);

CREATE INDEX IF NOT EXISTS idx_aggregated_content_source_id ON aggregated_content(source_id);
CREATE INDEX IF NOT EXISTS idx_aggregated_content_source_type ON aggregated_content(source_type);
CREATE INDEX IF NOT EXISTS idx_aggregated_content_published_at ON aggregated_content(published_at DESC);
CREATE INDEX IF NOT EXISTS idx_aggregated_content_created_at ON aggregated_content(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_aggregated_content_source_published ON aggregated_content(source_type, published_at DESC);

COMMENT ON TABLE aggregated_content IS 'Stores aggregated content from all parsers';
COMMENT ON COLUMN aggregated_content.external_id IS 'Article ID in the source system. Combined with source_type forms the unique key.';
COMMENT ON COLUMN aggregated_content.content IS 'Full content with S3 image URLs. Format indicated by content_format.';
COMMENT ON COLUMN aggregated_content.content_format IS 'Content format: HTML, MARKDOWN, PLAIN, RAW';
COMMENT ON COLUMN aggregated_content.source_id IS 'ID of the source (e.g., channel username, hub alias)';
COMMENT ON COLUMN aggregated_content.source_type IS 'Type of source: HABR, VCRU, TELEGRAM, RSS';
COMMENT ON COLUMN aggregated_content.media IS 'JSONB field with media attachments (images, videos)';
COMMENT ON COLUMN aggregated_content.metadata IS 'JSONB field with additional metadata';
-- rollback DROP TABLE IF EXISTS aggregated_content;

-- changeset aggregator:create-aggregated-content-trigger splitStatements:false
-- comment: Create updated_at trigger function and apply to aggregated_content

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_aggregated_content_updated_at ON aggregated_content;
CREATE TRIGGER update_aggregated_content_updated_at
    BEFORE UPDATE ON aggregated_content
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
-- rollback DROP TRIGGER IF EXISTS update_aggregated_content_updated_at ON aggregated_content;
-- rollback DROP FUNCTION IF EXISTS update_updated_at_column();
