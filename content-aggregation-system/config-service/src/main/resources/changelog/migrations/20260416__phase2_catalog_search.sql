-- liquibase formatted sql

-- changeset mattew:20260416__phase2_enable_pg_trgm splitStatements:false
-- preconditions onFail:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_extension WHERE extname = 'pg_trgm';

-- Description: Enable pg_trgm extension for trigram-based ILIKE acceleration
CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- rollback DROP EXTENSION IF EXISTS pg_trgm;

-- changeset mattew:20260416__phase2_dedup_sources_type_name splitStatements:false
-- preconditions onFail:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_constraint WHERE conname='uq_sources_type_name';

-- Description: Remove duplicates by (source_type, name), keep earliest created row
DELETE FROM config.sources a
  USING config.sources b
 WHERE a.source_type = b.source_type
   AND a.name = b.name
   AND a.created_at > b.created_at;
-- rollback SELECT 1;

-- changeset mattew:20260416__phase2_sources_type_name_unique
-- preconditions onFail:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_constraint WHERE conname='uq_sources_type_name';

-- Description: Unique constraint on (source_type, name) for race-safe dedup.
-- Pairs with application-level check in SourceService.create() that now treats
-- unique-violation as was_existing=true instead of an error.
ALTER TABLE config.sources
    ADD CONSTRAINT uq_sources_type_name UNIQUE (source_type, name);
-- rollback ALTER TABLE config.sources DROP CONSTRAINT IF EXISTS uq_sources_type_name;

-- changeset mattew:20260416__phase2_sources_name_trgm_index
-- preconditions onFail:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_indexes WHERE schemaname='config' AND indexname='idx_sources_name_trgm';

-- Description: GIN index over name with gin_trgm_ops for fast ILIKE substring search
CREATE INDEX idx_sources_name_trgm
    ON config.sources
    USING gin (name gin_trgm_ops);
-- rollback DROP INDEX IF EXISTS config.idx_sources_name_trgm;

-- changeset mattew:20260416__phase2_sources_created_at_desc_index
-- preconditions onFail:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_indexes WHERE schemaname='config' AND indexname='idx_sources_created_at_desc';

-- Description: B-tree index on (created_at DESC, id DESC) for keyset pagination of catalog
CREATE INDEX idx_sources_created_at_desc
    ON config.sources (created_at DESC, id DESC);
-- rollback DROP INDEX IF EXISTS config.idx_sources_created_at_desc;
