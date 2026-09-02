--liquibase formatted sql

--changeset parser-service:20260418-parser-001
ALTER TABLE data_flow.parser_tasks ADD COLUMN claimed_by VARCHAR(255);
ALTER TABLE data_flow.parser_tasks ADD COLUMN claimed_at TIMESTAMP;

--changeset parser-service:20260418-parser-002
CREATE INDEX idx_parser_tasks_execute
  ON data_flow.parser_tasks (status, source_type, created_at)
  WHERE status = 'PENDING';

--changeset parser-service:20260418-parser-003
CREATE UNIQUE INDEX uq_parser_tasks_active_source
  ON data_flow.parser_tasks (source_id)
  WHERE status IN ('PENDING', 'RUNNING');

--changeset parser-service:20260418-parser-004
CREATE INDEX idx_parser_tasks_stale
  ON data_flow.parser_tasks (claimed_at)
  WHERE status = 'RUNNING';
