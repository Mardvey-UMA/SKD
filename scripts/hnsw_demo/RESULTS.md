# Доказательство использования HNSW-индекса `idx_posts_features_embedding`

**Дата прогона:** 2026-05-30
**СУБД:** PostgreSQL 17 + pgvector (образ `pgvector/pgvector:pg17`), контейнер `skd-postgres`, БД `content_agg_db`.
**Объект:** `data_flow.posts_features.embedding vector(312)`, индекс
`idx_posts_features_embedding USING hnsw (embedding vector_cosine_ops) WITH (m=16, ef_construction=64)` (миграция rec-system `007_data_flow_schema.py`).
**Запрос:** метод `get_candidates_by_embedding` — `ORDER BY embedding <=> :qvec LIMIT 500` (Pool B, раздел 4.6.3).

Корпус для демонстрации: **100 000** постов со 312-мерными эмбеддингами
(порог из проектной документации rec-system — «HNSW индекс нужен при >50K постов»).

> Примечание: для HNSW обязателен `SET hnsw.ef_search = 500;` — иначе индекс
> возвращает не более `ef_search` (по умолчанию 40) кандидатов и `LIMIT 500`
> не наберётся. Это штатный параметр поиска по HNSW.

---

## Листинг 1. Корпус 100 000 постов — планировщик САМ выбирает HNSW Index Scan

```text
SET hnsw.ef_search = 500;

EXPLAIN (ANALYZE, BUFFERS)
SELECT post_id
FROM data_flow.posts_features
WHERE embedding IS NOT NULL
ORDER BY embedding <=> '[…312 значений…]'::vector
LIMIT 500;

                                   QUERY PLAN
--------------------------------------------------------------------------------
 Limit  (cost=4141.79..4864.44 rows=500 width=24)
        (actual time=17.991..19.769 rows=500 loops=1)
   Buffers: shared hit=11584
   ->  Index Scan using idx_posts_features_embedding on posts_features
              (cost=4141.79..148672.00 rows=100000 width=24)
              (actual time=17.990..19.736 rows=500 loops=1)
         Order By: (embedding <=> '[…312 значений…]'::vector)
         Filter: (embedding IS NOT NULL)
         Buffers: shared hit=11584
 Planning Time: 0.107 ms
 Execution Time: 19.902 ms
```

**Вывод:** узел `Index Scan using idx_posts_features_embedding` со строкой
`Order By: (embedding <=> …)` — индекс реально используется. 500 ближайших
постов отобраны за **19.9 мс** на корпусе из 100 000 постов
(`enable_seqscan` включён, по умолчанию — выбор сделан планировщиком).

---

## Листинг 2. Тот же запрос, `enable_seqscan = off` — применимость индекса

```text
SET enable_seqscan = off;
-- тот же запрос

 Limit  (actual time=7.185..7.721 rows=500 loops=1)
   ->  Index Scan using idx_posts_features_embedding on posts_features
              (actual time=7.184..7.694 rows=500 loops=1)
         Order By: (embedding <=> '[…312 значений…]'::vector)
 Execution Time: 7.753 ms
```

---

## Листинг 3. «ДО наполнения»: маленькая таблица (200 строк) — Seq Scan

```text
 Limit  (cost=44.14..44.64 rows=200) (actual time=0.074..0.092 rows=200 loops=1)
   ->  Sort  (cost=44.14..44.64 rows=200) (actual time=0.074..0.080 rows=200 loops=1)
         Sort Key: ((embedding <=> '[…]'::vector))
         Sort Method: quicksort  Memory: 39kB
         ->  Seq Scan on posts_small (cost=0.00..36.50 rows=200)
                     (actual time=0.004..0.040 rows=200 loops=1)
 Execution Time: 0.114 ms
```

**Вывод:** на малом объёме планировщик честно выбирает `Seq Scan` + `Sort`
(полный перебор дешевле) — это объясняет, почему «создан индекс ≠ индекс
используется», и почему демонстрацию необходимо проводить на целевом
объёме (>50K постов).

---

## Как воспроизвести

```bash
cd ~/SKD
docker compose up -d postgres
# 1) схема + 100K строк + HNSW-индекс
docker exec -i skd-postgres psql -U postgres -d content_agg_db < scripts/hnsw_demo/01_setup.sql
# 2) главный листинг (EXPLAIN ANALYZE, индекс используется)
docker exec -i skd-postgres psql -U postgres -d content_agg_db < scripts/hnsw_demo/02_explain.sql
# 3) (опц.) seqscan on/off на большом корпусе
docker exec -i skd-postgres psql -U postgres -d content_agg_db < scripts/hnsw_demo/03_before_after.sql
# 4) (опц.) Seq Scan на маленькой таблице
docker exec -i skd-postgres psql -U postgres -d content_agg_db < scripts/hnsw_demo/04_small_table_seqscan.sql
```
