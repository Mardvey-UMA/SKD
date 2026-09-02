-- =====================================================================
-- HNSW demo — доказательство: EXPLAIN ANALYZE на точном запросе
-- get_candidates_by_embedding (ORDER BY embedding <=> :vec LIMIT 500).
--
-- Берём реальный вектор ИЗ САМОЙ таблицы через \gset -> он подставляется
-- как литерал-константа, поэтому планировщик гарантированно может
-- использовать HNSW-индекс (idx_posts_features_embedding).
--
-- Запуск (psql, терминал — \gset работает только в psql):
--   docker exec -i skd-postgres psql -U postgres -d content_agg_db \
--       < /home/mattew/SKD/scripts/hnsw_demo/02_explain.sql
-- =====================================================================

SET search_path TO data_flow, public;
SET hnsw.ef_search = 500;   -- иначе индекс отдаёт <= 40 строк и LIMIT 500 не наберётся

-- Достаём один реальный вектор из таблицы как литерал :qvec
SELECT embedding AS qvec
FROM posts_features
WHERE embedding IS NOT NULL
LIMIT 1 \gset

-- Точный запрос Pool B (4.6.3) под EXPLAIN ANALYZE
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT post_id
FROM posts_features
WHERE embedding IS NOT NULL
ORDER BY embedding <=> :'qvec'::vector
LIMIT 500;
