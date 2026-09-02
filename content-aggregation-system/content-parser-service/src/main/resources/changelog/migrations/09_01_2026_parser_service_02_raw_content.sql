-- liquibase formatted sql
-- changeset parser-service:20260109-parser-003
-- comment: Create raw_content table for two-phase parsing with Kafka send tracking
-- date: 2026-01-09

CREATE TABLE IF NOT EXISTS raw_content (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id VARCHAR(255) NOT NULL,
    source_id UUID NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    raw_data JSONB NOT NULL,
    raw_media JSONB,
    downloaded_media JSONB,
    processing_status VARCHAR(50) DEFAULT 'PENDING',
    is_sent_to_kafka BOOLEAN DEFAULT false NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP DEFAULT NOW() NOT NULL,

    CONSTRAINT chk_raw_content_status CHECK (processing_status IN (
        'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'SKIPPED'
    )),
    CONSTRAINT uk_raw_content_source_external UNIQUE (source_type, external_id)
);

CREATE INDEX IF NOT EXISTS idx_raw_content_source
    ON raw_content(source_id, source_type);

CREATE INDEX IF NOT EXISTS idx_raw_content_status
    ON raw_content(processing_status);

CREATE INDEX IF NOT EXISTS idx_raw_content_received
    ON raw_content(received_at DESC);

CREATE INDEX IF NOT EXISTS idx_raw_content_pending
    ON raw_content(processing_status, received_at)
    WHERE processing_status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_raw_content_retry
    ON raw_content(processing_status, retry_count, received_at)
    WHERE processing_status = 'FAILED' AND retry_count < 5;

CREATE INDEX IF NOT EXISTS idx_raw_content_not_sent
    ON raw_content(is_sent_to_kafka, processing_status, received_at)
    WHERE is_sent_to_kafka = false AND processing_status = 'COMPLETED';

COMMENT ON TABLE raw_content IS 'Raw content from external sources before Kafka publishing';
COMMENT ON COLUMN raw_content.external_id IS 'Article ID in the source system (e.g., Habr article ID). Combined with source_type forms the unique key.';
COMMENT ON COLUMN raw_content.raw_data IS 'Original data from source in JSON format, including processed HTML with S3 URLs';
COMMENT ON COLUMN raw_content.raw_media IS 'Original media URLs from source';
COMMENT ON COLUMN raw_content.downloaded_media IS 'S3 paths after media download';
COMMENT ON COLUMN raw_content.processing_status IS 'PENDING, PROCESSING, COMPLETED, FAILED, SKIPPED';
COMMENT ON COLUMN raw_content.is_sent_to_kafka IS 'False until SendParsedContentJob successfully publishes to Kafka';

-- changeset parser-service:20260109-parser-004 splitStatements:false
-- comment: Create trigger for raw_content updated_at

CREATE OR REPLACE FUNCTION update_raw_content_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_raw_content_updated_at ON raw_content;

CREATE TRIGGER update_raw_content_updated_at
    BEFORE UPDATE ON raw_content
    FOR EACH ROW
    EXECUTE FUNCTION update_raw_content_updated_at();
