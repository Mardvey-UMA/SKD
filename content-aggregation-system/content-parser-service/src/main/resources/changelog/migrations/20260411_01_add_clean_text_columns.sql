--liquibase formatted sql

--changeset platform:20260411-01-clean-text-columns
ALTER TABLE data_flow.raw_content
    ADD COLUMN clean_html TEXT,
    ADD COLUMN clean_text TEXT,
    ADD COLUMN preview_text TEXT,
    ADD COLUMN clean_text_length INTEGER,
    ADD COLUMN cleaning_status VARCHAR(20),
    ADD COLUMN cleaning_error TEXT,
    ADD COLUMN cleaning_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN cleaned_at TIMESTAMPTZ;

CREATE INDEX idx_raw_content_cleaning_pending
    ON data_flow.raw_content (received_at)
    WHERE clean_text IS NULL
      AND processing_status = 'COMPLETED'
      AND cleaning_attempts < 3;

COMMENT ON COLUMN data_flow.raw_content.clean_html IS 'jsoup-sanitized HTML, whitelist-only tags, relative S3 paths';
COMMENT ON COLUMN data_flow.raw_content.clean_text IS 'Plain text extracted from clean_html, NFC normalized';
COMMENT ON COLUMN data_flow.raw_content.preview_text IS 'First ~300 chars of clean_text, sentence-aware';
COMMENT ON COLUMN data_flow.raw_content.cleaning_status IS 'NULL=not cleaned, OK, FAILED, OVERSIZED';
