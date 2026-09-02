--liquibase formatted sql

--changeset platform:20260411-02-published-content-clean-fields
ALTER TABLE data_flow.published_content
    ADD COLUMN content_html TEXT,
    ADD COLUMN content_text TEXT,
    ADD COLUMN preview_text TEXT,
    ADD COLUMN content_text_length INTEGER;

COMMENT ON COLUMN data_flow.published_content.content_html IS 'Sanitized HTML from raw_content.clean_html, relative S3 paths';
COMMENT ON COLUMN data_flow.published_content.content_text IS 'Plain text from raw_content.clean_text';
COMMENT ON COLUMN data_flow.published_content.preview_text IS 'Card preview from raw_content.preview_text';
