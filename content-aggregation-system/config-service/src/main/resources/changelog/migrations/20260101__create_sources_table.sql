-- liquibase formatted sql
-- changeset mattew:20260101__create_sources_table

-- Description: Create sources table with indexes and updated_at trigger
-- Date: 2026-01-01

CREATE TABLE IF NOT EXISTS sources (
    id                        UUID         NOT NULL DEFAULT gen_random_uuid(),
    source_type               VARCHAR(50)  NOT NULL,
    name                      VARCHAR(255) NOT NULL,
    url                       VARCHAR(2048),
    update_frequency_minutes  INTEGER      NOT NULL DEFAULT 60,
    is_active                 BOOLEAN      NOT NULL DEFAULT true,
    parameters                JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_sources PRIMARY KEY (id),
    CONSTRAINT chk_sources_type_valid CHECK (source_type IN ('HABR', 'VCRU', 'TELEGRAM', 'RSS')),
    CONSTRAINT chk_sources_frequency_positive CHECK (update_frequency_minutes > 0)
);

CREATE INDEX idx_sources_type        ON sources(source_type);
CREATE INDEX idx_sources_active      ON sources(is_active);
CREATE INDEX idx_sources_type_active ON sources(source_type, is_active);
CREATE INDEX idx_sources_frequency   ON sources(update_frequency_minutes);
-- rollback DROP TABLE IF EXISTS sources;

-- changeset mattew:20260101__create_sources_updated_at_function splitStatements:false

-- Description: Auto-update updated_at on row modification
-- Date: 2026-01-01

CREATE OR REPLACE FUNCTION update_sources_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
-- rollback DROP FUNCTION IF EXISTS update_sources_updated_at();

-- changeset mattew:20260101__create_sources_updated_at_trigger splitStatements:false

-- Description: Attach updated_at trigger to sources table
-- Date: 2026-01-01

CREATE TRIGGER trg_sources_updated_at
    BEFORE UPDATE ON sources
    FOR EACH ROW
    EXECUTE FUNCTION update_sources_updated_at();
-- rollback DROP TRIGGER IF EXISTS trg_sources_updated_at ON sources;

-- changeset mattew:20260101__sources_comments splitStatements:true

-- Description: Column comments for sources table documentation
-- Date: 2026-01-01

COMMENT ON TABLE  sources                              IS 'Configuration table for content sources';
COMMENT ON COLUMN sources.id                           IS 'Unique identifier for the source';
COMMENT ON COLUMN sources.source_type                  IS 'Type of the source: HABR, VCRU, TELEGRAM, RSS';
COMMENT ON COLUMN sources.name                         IS 'Human-readable name of the source';
COMMENT ON COLUMN sources.url                          IS 'URL of the source (optional for some types)';
COMMENT ON COLUMN sources.update_frequency_minutes     IS 'How often to fetch updates (in minutes)';
COMMENT ON COLUMN sources.is_active                    IS 'Whether the source is currently active';
COMMENT ON COLUMN sources.parameters                   IS 'Source-specific configuration in JSONB format';
COMMENT ON COLUMN sources.created_at                   IS 'Timestamp when the source was created';
COMMENT ON COLUMN sources.updated_at                   IS 'Timestamp when the source was last updated';
-- rollback SELECT 1;
