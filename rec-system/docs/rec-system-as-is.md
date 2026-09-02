# Сервис рекомендаций (rec-system) — описание AS-IS

**Дата:** 2026-04-04
**Версия:** 1.2.0
**Стек:** Python 3.12, FastAPI, SQLAlchemy async, aiokafka, redis, dependency-injector
**Порт:** 8000
**БД:** PostgreSQL 16 + pgvector, схема `data_flow` в `content_agg_db`

---

## 1. Обзор архитектуры

rec-system — сервис персонализированных рекомендаций для ленты новостей. Получает контент из shared-БД (parser пишет raw_content), обрабатывает NLP-пайплайном, строит профили пользователей на основе их взаимодействий и генерирует персонализированную ленту.

```
                    ┌────────────────────────────────────────┐
                    │           rec-system :8000              │
                    ├────────────────────────────────────────┤
   HTTP IN          │  POST /recommendations                 │ ← feed-service
                    │  GET  /recommendations/cold-start       │ ← feed-service
                    │  POST /onboarding                      │ ← user-service
                    │  GET  /categories                      │ ← user-service
                    │  GET  /health                          │ ← load balancer
                    │                                        │
   KAFKA IN         │  user.created                     ←────│── user-service
                    │  user.interactions.batch           ←────│── user-interactions-service
                    │                                        │
   KAFKA OUT        │  recommendations.updated          ─────│─→ feed-service
                    │                                        │
   BACKGROUND       │  APScheduler:                          │
                    │    - Content NLP processing (5 мин)    │
                    │    - Profile update (5 мин)            │
                    │    - Cold-start refresh (60 мин)       │
                    │    - History cleanup (24 часа)         │
                    │                                        │
   STORAGE          │  PostgreSQL + pgvector (312-dim)       │
                    │  Redis (debounce + cold-start cache)   │
                    │  Kafka (aiokafka)                      │
                    └────────────────────────────────────────┘
```

### Обязательные зависимости (без них сервис НЕ стартует)

- **PostgreSQL** — профили, фичи контента, история, конфиг
- **Kafka** — consumer (user.created, user.interactions.batch), producer (recommendations.updated)
- **Redis** — debounce ключи, cold-start cache

---

## 2. HTTP API

### 2.1 GET /health

Проверка состояния. Вызывается load balancer / Docker healthcheck.

```
Request:  GET /health
Response: {"status": "ok", "version": "1.2.0"}
```

Версия из переменной окружения `APP_VERSION` (default: `0.1.0`).

**Файлы:** `src/presentation/api/health_router.py`

---

### 2.2 POST /recommendations

Генерация персонализированной ленты. Основной endpoint.

```
Request:  POST /recommendations
Body:     {"user_id": "UUID", "count": 120}
Response: {"user_id": "UUID", "items": ["UUID", ...], "count": 120, "generated_at": "ISO8601"}
Error:    404 {"error": "user_not_found", "user_id": "UUID", "message": "..."}
```

**Что происходит внутри (файл `src/application/use_cases/get_recommendations.py`):**

1. Загрузка профиля из `rec_profiles` → 404 если нет
2. Загрузка `recommendation_history` для user_id → set[UUID] уже показанных
3. Вызов `GenerateFeedUseCase.execute(user_id, count + buffer)` (внутренний пайплайн, см. п.5)
4. Фильтрация: убрать items из recommendation_history
5. Обрезка scores — возвращаются только UUID, без числовых оценок
6. Запись возвращённых items в `recommendation_history` (ON CONFLICT DO NOTHING)
7. Возврат `{user_id, items: [UUID], count, generated_at}`

**Гарантии:**
- items упорядочены по релевантности (первый = самый подходящий)
- Нет дупликатов
- Нет ранее рекомендованных (recommendation_history)
- Все ID существуют в `published_content`
- Если контента меньше чем count → возвращает сколько есть, без ошибки

**Файлы:**
- Router: `src/presentation/api/recommendations_router.py`
- Use case: `src/application/use_cases/get_recommendations.py`
- Внутренний пайплайн: `src/application/use_cases/generate_feed.py`

---

### 2.3 GET /recommendations/cold-start

Список популярного контента для новых пользователей или fallback при недоступности.

```
Request:  GET /recommendations/cold-start?count=30
Response: {"items": ["UUID", ...], "count": 30, "generated_at": "ISO8601"}
```

**Логика:**
1. Проверяет Redis ключ `rec:cold-start:trending`
2. **Cache hit:** парсит JSON, возвращает slice по count
3. **Cache miss:** запрашивает `published_content ORDER BY published_at DESC LIMIT 50`, записывает в Redis (TTL 3600s), возвращает

**Параметры:**
- `count` — default 30, max 50
- Без user context — глобальный список

**Redis:**
- Ключ: `rec:cold-start:trending`
- TTL: 3600s (1 час)
- Заполняется: APScheduler job при старте + каждые 60 минут; auto-populate при cache miss

**Файлы:**
- Router: `src/presentation/api/recommendations_router.py`
- Use case: `src/application/use_cases/get_cold_start.py`
- Refresh job: `src/infrastructure/scheduling/job_scheduler.py:132-147`

---

### 2.4 POST /onboarding

Инициализация профиля после выбора категорий. Вызывается user-service.

```
Request:  POST /onboarding
Body:     {"user_id": "UUID", "categories": ["технологии", "наука", "бизнес"], "source_content_ids": ["UUID"]}
Response: {"user_id": "UUID", "profile_initialized": true}
Error:    404 {"error": "user_not_found", ...}
Error:    422 если categories < 3
```

**Что происходит внутри (файл `src/application/use_cases/onboard_user.py`):**

1. Загрузка профиля → 404 если нет (создаётся через Kafka `user.created`)
2. Валидация categories: все должны быть active ID из таблицы `categories`
3. Создание TopicVector: выбранные категории получают равный вес (~0.33), остальные — baseline (0.01), L1-нормализация
4. Если `source_content_ids` переданы: загрузка embeddings этих постов из `posts_features`, усреднение, L2-нормализация → устанавливается как profile.embedding
5. `cold_start = false`
6. Сохранение профиля
7. Публикация Kafka event `recommendations.updated` с `reason: "onboarding_complete"` (без debounce)

**Re-onboarding:** Если вызвать повторно — topic_vector полностью перезаписывается новыми категориями.

**Файлы:**
- Router: `src/presentation/api/onboarding_router.py`
- Use case: `src/application/use_cases/onboard_user.py`
- Domain: `src/domain/services/onboarding_service.py`

---

### 2.5 GET /categories

Справочник категорий для UI онбординга.

```
Request:  GET /categories?locale=ru
Response: {
  "categories": [
    {"id": "политика", "name": "Политика", "icon": "landmark"},
    {"id": "технологии", "name": "Технологии", "icon": "laptop"},
    ...
  ],
  "min_select": 3,
  "max_select": 5
}
```

**Источник данных:** таблица `data_flow.categories` (18 записей). Это единый источник истины для:
- UI онбординга (GET /categories)
- Валидации при POST /onboarding
- NLI-классификации топиков (candidate labels)
- TopicVector (ключи словаря)

**18 категорий:** политика, экономика, технологии, наука, спорт, культура, общество, происшествия, международные новости, бизнес, финансы, образование, здоровье, развлечения, криминал, армия, природа, транспорт.

**Файлы:**
- Router: `src/presentation/api/categories_router.py`
- Use case: `src/application/use_cases/get_categories.py`
- Repository: `src/infrastructure/persistence/pg_category_repository.py`

---

## 3. Kafka — входящие события

### 3.1 user.created

| Параметр | Значение |
|----------|----------|
| Topic | `user.created` |
| Consumer Group | `rec-system-user-events` |
| Формат | JSON |
| Продьюсер | user-service (будущий) |

```json
{"event_type": "user.created", "user_id": "UUID", "email": "string", "timestamp": "ISO8601"}
```

**Обработка (файл `src/application/use_cases/handle_user_created.py`):**
1. Проверка: профиль уже существует? → пропустить (идемпотентность)
2. Создание пустого профиля: zero vector 312-dim, uniform topic_vector, `interaction_count=0`, `cold_start=true`
3. Сохранение в `rec_profiles`

### 3.2 user.interactions.batch

| Параметр | Значение |
|----------|----------|
| Topic | `user.interactions.batch` |
| Consumer Group | `rec-system-interactions` |
| Формат | JSON |
| Продьюсер | user-interactions-service (будущий) |

```json
{
  "event_type": "user.interactions.batch",
  "user_id": "UUID",
  "interactions": [
    {"content_id": "UUID", "action_type": "click", "duration_sec": null, "timestamp": "ISO8601"},
    {"content_id": "UUID", "action_type": "view", "duration_sec": 45, "timestamp": "ISO8601"}
  ],
  "batch_ts": "ISO8601"
}
```

**Маппинг action_type → event_type (для SignalClassifier):**

| Kafka action_type | → Внутренний event_type | Signal weight |
|-------------------|------------------------|---------------|
| `view` (duration ≥ 2s) | IMPRESSION | +0.15 (read) |
| `view` (duration < 2s) | IMPRESSION | -0.05 (skip) |
| `click` | LIKE | +0.60 |
| `save` | BOOKMARK | +0.80 |
| `share` | BOOKMARK | +0.80 |
| `hide` | DISLIKE | -0.70 |
| `scroll_past` | IMPRESSION | -0.05 (no duration → skip) |

**Обработка (файл `src/application/use_cases/handle_interactions_batch.py`):**

1. Загрузка профиля (создание пустого если нет — defensive)
2. Конвертация InteractionItem → UserInteraction (маппинг action_type)
3. Классификация сигналов через SignalClassifier
4. Загрузка content features для всех постов из батча
5. **EMA-обновление профиля** через ProfileUpdater.apply_batch()
6. Сохранение обновлённого профиля
7. **Persist entity interests** — upsert entity changes + decay (×0.95) + cleanup (>30 дней)
8. **Запись в user_interactions** с `processed=true` (чтобы background job не обработал повторно)
9. Проверка сильных сигналов (click, share, save, hide)
10. Если есть → проверка Redis debounce → публикация `recommendations.updated`

---

## 4. Kafka — исходящие события

### 4.1 recommendations.updated

| Параметр | Значение |
|----------|----------|
| Topic | `recommendations.updated` |
| Формат | JSON |
| Consumer | feed-service (будущий) |

```json
{"event_type": "recommendations.updated", "user_id": "UUID", "reason": "onboarding_complete", "timestamp": "ISO8601"}
```

**Когда публикуется:**

| Триггер | reason | Debounce |
|---------|--------|----------|
| POST /onboarding завершён | `onboarding_complete` | нет — всегда сразу |
| Batch с сильным сигналом | `interactions_processed` | 15 минут (Redis) |

**Механизм debounce (файл `src/application/use_cases/handle_interactions_batch.py:145-162`):**
1. GET `rec:invalidation:last:{user_id}` из Redis
2. Если ключ есть (< 15 мин) → пропустить публикацию
3. Если ключа нет → опубликовать в Kafka + SETEX ключ с TTL 900s

---

## 5. Внутренний пайплайн генерации ленты

**Файл: `src/application/use_cases/generate_feed.py`**

```
Вход: user_id, feed_size

Step 0: Инлайн обновление профиля
  → Загрузка unprocessed events из user_interactions
  → SignalClassifier → ProfileUpdater (EMA)
  → Сохранение обновлённого профиля

Step 1: Двухэтапная выборка кандидатов
  A) 500 постов по свежести (ORDER BY processed_at DESC)
  B) 500 постов по embedding similarity (pgvector HNSW <=>)
  → Дедупликация по post_id

Step 2: Dedup-кластеризация
  → Загрузка графа articles + similarities из dedup-system
  → EXACT/DUPLICATE → Union-Find кластеры → оставить 1 на кластер (самый свежий)

Step 3: Скоринг (6-компонентная формула)
  → Для каждого кандидата вычисляется score [0,1]

Step 4: Сортировка по score DESC

Step 5: Diversity filter
  → Topic streak limit: max 3 поста подряд с одной темой
  → Global topic cap: max 40% ленты на одну тему
  → RELATED spacing: gap ≥ 3 между related-постами (из dedup similarities)

Step 6: Маппинг на published_content IDs
  → raw_content.id → published_content.id
  → Посты без маппинга исключаются

Выход: [{post_id: published_content.id, score: float}, ...]
```

---

## 6. Формула скоринга

**Файл: `src/domain/services/scorer.py`**

```
score = 0.30 × topic_match
      + 0.25 × embedding_sim
      + 0.15 × entity_match
      + 0.05 × sentiment_match
      + 0.15 × freshness
      + 0.10 × format_match
```

### 6.1 topic_match (30%)

```python
# src/domain/services/scorer.py:107-114
score = sum(profile.topic_vector[topic] * post.topic_score[topic] for topic in post.top3_topics)
# Clamped to [0, 1]
```

Пользовательский topic_vector (18 значений, сумма = 1.0) умножается на confidence scores поста (top-3 темы от NLI классификатора).

### 6.2 embedding_sim (25%)

```python
# src/domain/services/scorer.py:117-121
score = max(0, cosine_similarity(profile.embedding, post.embedding))
```

Cosine similarity между 312-dim embedding пользователя и поста (rubert-tiny2).

### 6.3 entity_match (15%)

```python
# src/domain/services/scorer.py:123-129
matching = count(post.entities ∩ user.entity_interests)
score = min(matching / 3, 1.0)
```

Количество совпадающих NER-сущностей (персоны, организации, локации) делённое на 3.

### 6.4 sentiment_match (5%)

```python
# src/domain/services/scorer.py:131-135
score = profile.sentiment_prefs[post.sentiment]  # POSITIVE / NEGATIVE / NEUTRAL
```

Предпочтение по тональности из профиля пользователя.

### 6.5 freshness (15%)

```python
# src/domain/services/scorer.py:137-142
hours = (now - post.processed_at).total_hours()
score = exp(-0.693 * hours / halflife)  # halflife = 48h
```

Экспоненциальное затухание: 100% для нового, 50% через 48 часов, 25% через 96 часов.

### 6.6 format_match (10%)

```python
# src/domain/services/scorer.py:144-154
length_match = 1 - |profile.avg_length - post.word_count| / max(...)
complexity_match = 1 - |profile.avg_complexity - post.complexity|
score = length_match * complexity_match
```

Близость формата к предпочтениям пользователя.

### Cold start — перераспределение весов

Когда компонент "мёртвый" (embedding=null для embedding_sim, нет entity_interests для entity_match), его вес перераспределяется пропорционально между активными компонентами. Сумма всегда = 1.0.

---

## 7. Профиль пользователя и EMA-обновление

**Таблица:** `data_flow.rec_profiles`

| Поле | Тип | Описание |
|------|-----|----------|
| user_id | UUID PK | |
| topic_vector | JSONB | `{"технологии": 0.32, "наука": 0.28, ...}` — L1-нормализован, сумма=1 |
| embedding | vector(312) | Усреднённый embedding интересов (rubert-tiny2) |
| sentiment_prefs | JSONB | `{"POSITIVE": 0.4, "NEGATIVE": 0.3, "NEUTRAL": 0.3}` |
| format_prefs | JSONB | `{"avg_length": 200, "avg_complexity": 0.5}` |
| interaction_count | INTEGER | Сколько событий обработано |
| cold_start | BOOLEAN | true до онбординга, false после |

### EMA-формулы (lr = 0.08)

**Файл: `src/domain/services/profile_updater.py`**

При каждом взаимодействии с weight ≠ 0:

- **topic_vector:** `topic[t] += lr × weight × post.topic_score[t]` для каждого из top-3 топиков, затем L1-нормализация
- **embedding:** `new = (1 - alpha) × old + alpha × post.embedding`, где `alpha = lr × |weight|`, затем L2-нормализация. Только для положительных сигналов (weight > 0)
- **sentiment_prefs:** `pref[post.sentiment] += lr × weight × 0.5`, затем нормализация
- **format_prefs:** EMA на word_count и complexity (только положительные сигналы)
- **entity_interests:** при signal weight ≥ 0.4 → UPSERT weight += 1, затем decay ×0.95, cleanup > 30 дней

---

## 8. Redis

### Ключи

| Ключ | Тип | TTL | Назначение |
|------|-----|-----|------------|
| `rec:cold-start:trending` | String (JSON) | 3600s | Кеш trending-списка для cold-start endpoint |
| `rec:invalidation:last:{user_id}` | String ("1") | 900s | Debounce для recommendations.updated (15 мин) |

### Жизненный цикл cold-start cache

1. **Startup** → APScheduler job запускается сразу (`next_run_time=now()`), запрашивает 50 свежих published_content, пишет JSON в Redis
2. **Каждые 60 мин** → job перезаписывает кеш
3. **GET /cold-start при cache miss** → use case запрашивает БД и записывает в Redis
4. **TTL 3600s** → кеш живёт 1 час, обновляется job-ом раньше

### Жизненный цикл debounce

1. **Kafka batch с click/save/share/hide** → проверка `GET rec:invalidation:last:{user_id}`
2. **Ключа нет** → публикация Kafka event + `SETEX key 900 "1"`
3. **Ключ есть** → пропуск публикации, профиль всё равно обновляется
4. **Через 15 минут** → TTL истекает, следующий batch с сильным сигналом опубликует event
5. **POST /onboarding** → публикация без debounce (всегда сразу)

---

## 9. Background Jobs (APScheduler)

| Job | Интервал | Что делает |
|-----|----------|------------|
| Content Processing | 5 мин | Polls `raw_content` WHERE COMPLETED AND NOT is_processed_by_rec → NLP pipeline → saves to `posts_features` + sets flag |
| Profile Update | 5 мин | Reads `user_interactions` WHERE processed=false → classify signals → EMA update → mark processed |
| Cold-Start Refresh | 60 мин (+ при старте) | Queries 50 most recent `published_content` → writes JSON to Redis |
| History Cleanup | 24 часа | DELETE from `recommendation_history` WHERE recommended_at < now() - 30 days |

---

## 10. NLP-пайплайн обработки контента

**Файл: `src/application/use_cases/process_content.py`**

Для каждого необработанного поста из `raw_content`:

1. **Текст:** `title + " " + content` (если title есть) или только `content`. Truncate до 2000 символов для BERT
2. **Topics:** zero-shot NLI через `rubert-base-cased-nli-threeway` → top-3 из 18 категорий с confidence scores
3. **Sentiment:** `rubert-base-cased-sentiment` → POSITIVE/NEGATIVE/NEUTRAL + score
4. **NER:** spaCy `ru_core_news_lg` → persons, organizations, locations
5. **Embedding:** `rubert-tiny2` (SentenceTransformer) → 312-dim L2-normalized vector
6. **Text metrics:** word_count, text_length, complexity (Flesch-Oborneva), reading_time, is_short_form (<50 words), is_long_form (≥300 words)
7. **UPSERT** в `posts_features`, UPDATE `raw_content SET is_processed_by_rec = true`

---

## 11. Таблицы rec-system в data_flow

### Owned (rec-system пишет)

| Таблица | Назначение | Миграция |
|---------|------------|----------|
| `posts_features` | NLP-фичи контента: top-3 топиков, sentiment, NER, embedding 312-dim, text metrics | 007 |
| `rec_profiles` | Профили пользователей: topic_vector, embedding, sentiment_prefs, format_prefs, cold_start | 007, 010 |
| `rec_entity_interests` | Персональные интересы к NER-сущностям: (user_id, entity_type, entity_name) → weight | 007 |
| `rec_config` | Конфигурация: signal_weights, scoring_weights, profile_params, ranking_params, onboarding_params, dedup_params | 007 |
| `categories` | Справочник 18 NLP-категорий. Единый источник для онбординга, скоринга, NLI | 008 |
| `recommendation_history` | Что уже рекомендовали: (user_id, content_id) → recommended_at. Cleanup >30 дней | 009 |
| `user_interactions` | Записи из Kafka batches: event_type, duration_ms, scroll_pct. processed=true из Kafka, false из DB-polling | 007 |

### Cross-system reads (rec-system только читает)

| Таблица | Owner | Что читает rec-system |
|---------|-------|----------------------|
| `raw_content` | parser (Kotlin) | Polls WHERE COMPLETED AND NOT is_processed_by_rec. UPDATE flag. |
| `articles` | dedup (Python) | JOIN для dedup clustering (raw_content_id → article_id) |
| `similarities` | dedup (Python) | EXACT/DUPLICATE → clustering, RELATED → spacing gap=3 |
| `published_content` | parser (Kotlin) | Маппинг raw→published ID; cold-start list ORDER BY published_at |

### Удалённые

| Таблица | Причина удаления | Миграция |
|---------|-----------------|----------|
| `user_disliked_posts` | Избыточна — дизлайкнутый контент уже в recommendation_history | 011 |

---

## 12. Переменные окружения

| Переменная | Default | Обязательность | Описание |
|-----------|---------|---------------|----------|
| `DATABASE_URL` | `postgresql+asyncpg://rec_user:rec_pass@localhost:5432/content_agg_db` | обязательна | PostgreSQL |
| `REDIS_URL` | `redis://localhost:6379/0` | обязательна | Redis |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | обязательна | Kafka |
| `APP_VERSION` | `0.1.0` | нет | Версия для GET /health |
| `COLD_START_REFRESH_MINUTES` | `60` | нет | Интервал обновления cold-start cache |
| `HISTORY_CLEANUP_DAYS` | `30` | нет | Сколько дней хранить recommendation_history |

---

## 13. Docker

В `content-aggregation-system/docker-compose.yml`:

- **rec-alembic** — одноразовый контейнер, запускает `alembic upgrade rec_data_flow@head`
- **rec-worker** — FastAPI + APScheduler + Kafka consumers. Порт 8000. GPU для NLP моделей
- **redis** — Redis 7 Alpine. Порт 6379

---

## 14. Тесты

- **670 unit тестов** — покрывают все use cases, domain services, repositories, schemas, routers
- **33 integration теста** — миграции через testcontainers
- 1 pre-existing failure: spaCy entity extractor (NER нормализация)

---

## 15. Известные ограничения

- NER без нормализации сущностей ("Путин" vs "В. Путин" = 2 разные сущности)
- "Международные новости" доминируют в корпусе (~60%)
- Topic confidence ~0.66 average (шум zero-shot NLI)
- `is_long_form` threshold 300 words — triggers на <1% контента
- PGVector: HNSW индекс нужен при >50K постов
- `rec_config` не кешируется (DB read per request)
- Kafka consumer groups не сохраняют offsets при рестарте в dev-режиме
