-- =====================================================================
-- HNSW demo — (опционально) методическое усиление: показать ОБА плана.
-- Рецензент сам предлагает это как «дополнительно».
--
-- ВАЖНО: запускать на УЖЕ наполненной таблице (после 01_setup.sql).
-- enable_seqscan = off НЕ нужен на 100K — индекс выбирается сам.
-- Этот блок показывает, что на МАЛЕНЬКОЙ выборке планировщик честно
-- предпочитает Seq Scan (как и пишет рецензент), а индекс при этом
-- рабочий и применим.
--
-- Запуск:
--   docker exec -i skd-postgres psql -U postgres -d content_agg_db \
--       < /home/mattew/SKD/scripts/hnsw_demo/03_before_after.sql
-- =====================================================================

SET search_path TO data_flow, public;
SET hnsw.ef_search = 500;

SELECT embedding AS qvec
FROM posts_features
WHERE embedding IS NOT NULL
LIMIT 1 \gset

\echo '=== A. БОЛЬШОЙ корпус (100K): планировщик САМ берёт HNSW Index Scan ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT post_id FROM posts_features
WHERE embedding IS NOT NULL
ORDER BY embedding <=> :'qvec'::vector
LIMIT 500;

\echo '=== B. Тот же запрос с принудительным enable_seqscan=off (демонстрация применимости индекса) ==='
SET enable_seqscan = off;
EXPLAIN (ANALYZE, BUFFERS)
SELECT post_id FROM posts_features
WHERE embedding IS NOT NULL
ORDER BY embedding <=> :'qvec'::vector
LIMIT 500;
RESET enable_seqscan;
