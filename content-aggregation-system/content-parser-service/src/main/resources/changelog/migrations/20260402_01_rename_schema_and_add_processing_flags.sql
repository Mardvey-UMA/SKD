--liquibase formatted sql

--changeset integration:rename-parser-to-data-flow
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'parser'
ALTER SCHEMA parser RENAME TO data_flow;

--changeset integration:raw-content-drop-kafka-index
DROP INDEX IF EXISTS idx_raw_content_not_sent;

--changeset integration:raw-content-drop-kafka-flag
ALTER TABLE raw_content DROP COLUMN IF EXISTS is_sent_to_kafka;

--changeset integration:raw-content-add-dedup-flag
ALTER TABLE raw_content ADD COLUMN is_processed_by_dedup BOOLEAN NOT NULL DEFAULT false;

--changeset integration:raw-content-add-published-flag
ALTER TABLE raw_content ADD COLUMN is_published BOOLEAN NOT NULL DEFAULT false;

--changeset integration:raw-content-idx-dedup-pending
CREATE INDEX idx_raw_content_dedup_pending
    ON raw_content (id)
    WHERE processing_status = 'COMPLETED'
      AND is_processed_by_dedup = false;

--changeset integration:raw-content-idx-publish-ready
CREATE INDEX idx_raw_content_publish_ready
    ON raw_content (id)
    WHERE processing_status = 'COMPLETED'
      AND is_processed_by_dedup = true
      AND is_published = false;
