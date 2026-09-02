-- liquibase formatted sql
-- changeset parser-service:20260109-parser-001
-- comment: Create parser_tasks table
-- date: 2026-01-09

-- Create tables for parser service
CREATE TABLE IF NOT EXISTS parser_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    task_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_parser_tasks_source_id
    ON parser_tasks(source_id);

CREATE INDEX IF NOT EXISTS idx_parser_tasks_status
    ON parser_tasks(status);

CREATE INDEX IF NOT EXISTS idx_parser_tasks_scheduled_at
    ON parser_tasks(scheduled_at);

CREATE INDEX IF NOT EXISTS idx_parser_tasks_pending
    ON parser_tasks(status, scheduled_at)
    WHERE status = 'PENDING';

COMMENT ON TABLE parser_tasks IS 'Scheduled parsing tasks for content sources';

-- changeset parser-service:20260109-parser-002 splitStatements:false
-- comment: Create trigger for parser_tasks updated_at

CREATE OR REPLACE FUNCTION update_parser_tasks_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_parser_tasks_updated_at ON parser_tasks;

CREATE TRIGGER update_parser_tasks_updated_at
    BEFORE UPDATE ON parser_tasks
    FOR EACH ROW
    EXECUTE FUNCTION update_parser_tasks_updated_at();
