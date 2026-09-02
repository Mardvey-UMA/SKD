-- =====================================================================
-- HNSW demo — setup: воспроизводит data_flow.posts_features из миграции
-- rec-system 007 и наполняет её ~100K строк с vector(312) эмбеддингами.
--
-- Цель: довести таблицу до целевого масштаба (>50K постов), при котором
-- планировщик PostgreSQL выбирает HNSW Index Scan, а не Seq Scan.
--
-- Векторы синтетические (случайные нормированные vector(312)) — для
-- доказательства ФАКТА использования индекса значения векторов
-- значения не имеют, планировщик смотрит только на объём и стоимость.
--
-- Запуск:
--   docker exec -i skd-postgres psql -U postgres -d content_agg_db \
--       < /home/mattew/SKD/scripts/hnsw_demo/01_setup.sql
-- =====================================================================

\timing on
SET search_path TO data_flow, public;   -- vector тип, <=> и opclass живут в data_flow

-- ---------------------------------------------------------------------
-- 1. Таблицы (DDL дословно из миграции 007_data_flow_schema.py).
--    raw_content нужна из-за FK posts_features.post_id -> raw_content.id.
--    Индексы создаём ПОСЛЕ загрузки данных (так HNSW строится быстрее).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS data_flow.raw_content (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id         VARCHAR,
    source_id           UUID,
    source_type         VARCHAR,
    raw_data            JSONB,
    processing_status   VARCHAR,
    is_processed_by_rec BOOLEAN NOT NULL DEFAULT FALSE,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS data_flow.posts_features (
    post_id                UUID NOT NULL REFERENCES data_flow.raw_content(id) ON DELETE CASCADE,
    source_id              UUID NOT NULL,
    source_type            VARCHAR(50) NOT NULL,
    text_length            INTEGER,
    word_count             INTEGER,
    reading_time           REAL,
    complexity             REAL,
    is_short_form          BOOLEAN,
    is_long_form           BOOLEAN,
    topic_1                VARCHAR(100),
    topic_1_score          REAL,
    topic_2                VARCHAR(100),
    topic_2_score          REAL,
    topic_3                VARCHAR(100),
    topic_3_score          REAL,
    sentiment              VARCHAR(20),
    sentiment_score        REAL,
    entities_persons       JSONB,
    entities_organizations JSONB,
    entities_locations     JSONB,
    embedding              vector(312),
    processed_at           TIMESTAMPTZ,
    PRIMARY KEY (post_id)
);

-- ---------------------------------------------------------------------
-- 2. Загрузка ~100K строк со СЛУЧАЙНЫМИ vector(312).
--    ВАЖНО: подзапрос ARRAY(SELECT random() ...) без ссылки на внешнюю
--    строку PostgreSQL считает ОДИН раз (InitPlan) -> все векторы вышли
--    бы одинаковыми. Условие `WHERE rc.id = rc.id` коррелирует подзапрос
--    с внешней строкой -> SubPlan пересчитывается на КАЖДОЙ строке ->
--    312 разных значений на строку.
-- ---------------------------------------------------------------------
WITH ins AS (
    INSERT INTO data_flow.raw_content (id, processing_status, is_processed_by_rec)
    SELECT gen_random_uuid(), 'COMPLETED', TRUE
    FROM generate_series(1, 100000)
    RETURNING id
)
INSERT INTO data_flow.posts_features (post_id, source_id, source_type, embedding, processed_at)
SELECT
    rc.id,
    gen_random_uuid(),
    'telegram',
    (ARRAY(SELECT random() FROM generate_series(1, 312) WHERE rc.id = rc.id))::vector,
    now()
FROM ins rc;

-- ---------------------------------------------------------------------
-- 3. HNSW-индекс (DDL дословно из миграции 007).
--    max_parallel_maintenance_workers=0 — параллельная сборка использует
--    /dev/shm (в контейнере всего 64MB) и падает с "No space left on
--    device"; без параллелизма сборка идёт в локальной памяти.
-- ---------------------------------------------------------------------
SET max_parallel_maintenance_workers = 0;
SET maintenance_work_mem = '400MB';
CREATE INDEX IF NOT EXISTS idx_posts_features_embedding
    ON data_flow.posts_features
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- ---------------------------------------------------------------------
-- 4. Обновить статистику, чтобы планировщик видел реальный объём.
-- ---------------------------------------------------------------------
ANALYZE data_flow.posts_features;

-- ---------------------------------------------------------------------
-- 5. Проверки: объём, не-NULL эмбеддинги, наличие индекса, его размер,
--    и что векторы РАЗНЫЕ (distinct по выборке ~= размеру выборки).
-- ---------------------------------------------------------------------
SELECT count(*) AS total_rows, count(embedding) AS with_embedding
FROM data_flow.posts_features;

SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'data_flow' AND tablename = 'posts_features';

SELECT pg_size_pretty(pg_relation_size('data_flow.idx_posts_features_embedding')) AS hnsw_index_size;

SELECT count(DISTINCT embedding::text) AS distinct_vectors_in_sample
FROM (SELECT embedding FROM data_flow.posts_features LIMIT 1000) s;
