-- =====================================================================
-- HNSW demo — "ДО наполнения": на маленькой таблице планировщик честно
-- выбирает Seq Scan (подтверждает тезис рецензента). Тот же запрос,
-- тот же HNSW-индекс — разница только в объёме данных.
--
-- Запуск:
--   docker exec -i skd-postgres psql -U postgres -d content_agg_db \
--       < /home/mattew/SKD/scripts/hnsw_demo/04_small_table_seqscan.sql
-- =====================================================================

SET search_path TO data_flow, public;
SET hnsw.ef_search = 500;

DROP TABLE IF EXISTS data_flow.posts_small CASCADE;
CREATE TABLE data_flow.posts_small (
    post_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    embedding vector(312)
);

INSERT INTO data_flow.posts_small (embedding)
SELECT (ARRAY(SELECT random() FROM generate_series(1, 312) WHERE g.i = g.i))::vector
FROM generate_series(1, 200) AS g(i);

SET max_parallel_maintenance_workers = 0;
CREATE INDEX idx_posts_small_embedding
    ON data_flow.posts_small
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
ANALYZE data_flow.posts_small;

SELECT embedding AS qv FROM data_flow.posts_small LIMIT 1 \gset

\echo '==== МАЛЕНЬКАЯ таблица (200 строк) -> ожидаем Seq Scan ===='
EXPLAIN (ANALYZE, BUFFERS)
SELECT post_id FROM data_flow.posts_small
ORDER BY embedding <=> :'qv'::vector
LIMIT 500;

DROP TABLE data_flow.posts_small CASCADE;
