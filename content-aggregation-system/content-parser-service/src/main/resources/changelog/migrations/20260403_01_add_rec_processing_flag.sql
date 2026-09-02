--liquibase formatted sql

--changeset integration:raw-content-add-rec-flag
ALTER TABLE raw_content
    ADD COLUMN is_processed_by_rec BOOLEAN NOT NULL DEFAULT false;

--changeset integration:raw-content-idx-rec-pending
CREATE INDEX idx_raw_content_rec_pending
    ON raw_content (id)
    WHERE processing_status = 'COMPLETED' AND is_processed_by_rec = false;

--changeset integration:raw-content-idx-publish-ready-v2
--comment: Replace old publish-ready index to require both dedup AND rec processing
DROP INDEX IF EXISTS data_flow.idx_raw_content_publish_ready;
CREATE INDEX idx_raw_content_publish_ready
    ON raw_content (received_at)
    WHERE processing_status = 'COMPLETED'
      AND is_processed_by_dedup = true
      AND is_processed_by_rec = true
      AND is_published = false;
