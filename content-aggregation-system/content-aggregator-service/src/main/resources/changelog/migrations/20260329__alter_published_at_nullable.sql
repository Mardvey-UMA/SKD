-- liquibase formatted sql
-- Migration: Make published_at nullable
-- Service: content-aggregator-service
-- Date: 2026-03-29

-- changeset aggregator:alter-published-at-nullable splitStatements:true
-- comment: Allow null published_at for content without publish date

ALTER TABLE aggregated_content ALTER COLUMN published_at DROP NOT NULL;
-- rollback ALTER TABLE aggregated_content ALTER COLUMN published_at SET NOT NULL;
