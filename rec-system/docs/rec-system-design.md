# Дизайн-документ: rec-system (рекомендательная система)

**Версия:** 1.2.0  
**Дата:** 2026-04-04  
**Стек:** Python 3.12 / FastAPI / SQLAlchemy async / aiokafka / redis / dependency-injector  
**Порт:** 8000  

---

## 1. Назначение

rec-system — сервис персонализированных рекомендаций контента. Получает профили пользователей через Kafka, обрабатывает контент через NLP-пайплайн, строит и обновляет пользовательские профили на основе взаимодействий, генерирует персонализированную ленту по 6-компонентной скоринговой формуле.

---

## 2. Зависимости (все обязательные)

| Сервис | Назначение | Сервис не стартует без него |
|--------|------------|---------------------------|
| PostgreSQL 16 + pgvector | Профили, фичи контента, история, конфиг | да |
| Kafka | user.created, user.interactions.batch, recommendations.updated | да |
| Redis | Debounce ключи, cold-start cache | да |

---

## 3. HTTP API — контракты

### 3.1 GET /health

```
→ 200 {"status": "ok", "version": "1.2.0"}
→ 503 {"status": "degraded", "reason": "model_loading"}
```

Версия из `APP_VERSION` env var.

---

### 3.2 POST /recommendations

**Вызывает:** feed-service  
**Когда:** Cache miss в Redis (нет закешированной ленты для этого пользователя)  
**SLA:** p99 < 2000ms (реальное ~20ms)

```json
// Request
{"user_id": "UUID", "count": 120}

// Response 200
{
  "user_id": "UUID",
  "items": ["published_content_id_1", "published_content_id_2", ...],
  "count": 120,
  "generated_at": "2026-04-04T12:00:00Z"
}

// Response 404
{"error": "user_not_found", "user_id": "UUID", "message": "No recommendation profile exists for this user"}
```

**Гарантии контракта:**
- `items` упорядочен по релевантности (первый = самый подходящий)
- Нет дупликатов
- Нет ранее рекомендованного контента (recommendation_history)
- Все ID существуют в `published_content`
- Если контента меньше `count` → возвращает сколько есть, count отражает реальное количество

**Внутренний пайплайн** (см. секцию 6).

---

### 3.3 GET /recommendations/cold-start

**Вызывает:** feed-service  
**Когда:** rec-system вернул 404 (профиль не создан) или сервис недоступен  
**SLA:** p99 < 100ms (реальное ~2ms, из Redis)

```json
// Request
GET /recommendations/cold-start?count=30

// Response 200
{
  "items": ["UUID", ...],
  "count": 30,
  "generated_at": "2026-04-04T11:00:00Z"
}
```

| Параметр | Тип | Default | Max | Описание |
|----------|-----|---------|-----|----------|
| count | int | 30 | 50 | Количество ID |

**Источник данных:**
1. Redis `rec:cold-start:trending` → JSON (cache hit, ~2ms)
2. Fallback: `SELECT id FROM published_content ORDER BY published_at DESC LIMIT 50` → пишет в Redis (TTL 3600s)

---

### 3.4 POST /onboarding

**Вызывает:** user-service  
**Когда:** Пользователь завершил обязательный онбординг (выбрал 3+ категории)  
**SLA:** p99 < 500ms (реальное ~10ms)

```json
// Request
{
  "user_id": "UUID",
  "categories": ["технологии", "наука", "бизнес"],
  "source_content_ids": ["UUID"]  // опционально, max 10
}

// Response 200
{"user_id": "UUID", "profile_initialized": true}

// Response 404
{"error": "user_not_found", "user_id": "UUID", "message": "..."}

// Response 422 — categories < 3
```

**Валидация:**
- `categories` — min 3, значения должны быть active ID из таблицы `data_flow.categories`
- `source_content_ids` — max 10, UUID из `posts_features` (для embedding warmup)

**Side effects:**
- topic_vector инициализируется из выбранных категорий (равные веса + baseline для остальных)
- Если `source_content_ids` → усредняет embeddings этих постов → начальный embedding профиля
- `cold_start = false`
- Публикует Kafka event `recommendations.updated` с `reason: "onboarding_complete"` (без debounce)

**Re-onboarding:** При повторном вызове topic_vector полностью перезаписывается.

---

### 3.5 GET /categories

**Вызывает:** user-service  
**Когда:** Экран онбординга загружается  
**SLA:** p99 < 100ms (реальное ~5ms)

```json
// Request
GET /categories?locale=ru

// Response 200
{
  "categories": [
    {"id": "политика", "name": "Политика", "icon": "landmark"},
    {"id": "экономика", "name": "Экономика", "icon": "chart-line"},
    {"id": "технологии", "name": "Технологии", "icon": "laptop"},
    {"id": "наука", "name": "Наука", "icon": "flask"},
    {"id": "спорт", "name": "Спорт", "icon": "trophy"},
    {"id": "культура", "name": "Культура", "icon": "palette"},
    {"id": "общество", "name": "Общество", "icon": "users"},
    {"id": "происшествия", "name": "Происшествия", "icon": "alert-triangle"},
    {"id": "международные новости", "name": "Международные новости", "icon": "globe"},
    {"id": "бизнес", "name": "Бизнес", "icon": "briefcase"},
    {"id": "финансы", "name": "Финансы", "icon": "dollar-sign"},
    {"id": "образование", "name": "Образование", "icon": "book-open"},
    {"id": "здоровье", "name": "Здоровье", "icon": "heart"},
    {"id": "развлечения", "name": "Развлечения", "icon": "film"},
    {"id": "криминал", "name": "Криминал", "icon": "shield"},
    {"id": "армия", "name": "Армия", "icon": "shield-alert"},
    {"id": "природа", "name": "Природа", "icon": "leaf"},
    {"id": "транспорт", "name": "Транспорт", "icon": "car"}
  ],
  "min_select": 3,
  "max_select": 5
}
```

**Источник:** таблица `data_flow.categories` (WHERE is_active=true ORDER BY sort_order). Это единый источник истины для онбординга, NLI-классификации, TopicVector и скоринга.

---

## 4. Kafka — входящие события

### 4.1 user.created

| Параметр | Значение |
|----------|----------|
| Topic | `user.created` |
| Consumer Group | `rec-system-user-events` |
| Key | user_id |
| Partitions | 6 |
| Producer | user-service (будущий) |

```json
{
  "event_type": "user.created",
  "user_id": "UUID",
  "email": "string",
  "timestamp": "ISO8601"
}
```

**Обработка:** Создание пустого профиля (zero vector, cold_start=true, interaction_count=0). Идемпотентно — если профиль есть, пропускает.

### 4.2 user.interactions.batch

| Параметр | Значение |
|----------|----------|
| Topic | `user.interactions.batch` |
| Consumer Group | `rec-system-interactions` |
| Key | user_id |
| Partitions | 12 |
| Producer | user-interactions-service (будущий) |

```json
{
  "event_type": "user.interactions.batch",
  "user_id": "UUID",
  "interactions": [
    {
      "content_id": "UUID",
      "action_type": "view|click|scroll_past|share|save|hide",
      "duration_sec": 45,
      "timestamp": "ISO8601"
    }
  ],
  "batch_ts": "ISO8601"
}
```

**Маппинг action_type → Signal:**

| action_type | → Event Type | Weight | Сильный сигнал? |
|-------------|-------------|--------|-----------------|
| view (≥2s) | IMPRESSION READ | +0.15 | нет |
| view (<2s) | IMPRESSION SKIP | -0.05 | нет |
| click | LIKE | +0.60 | **да** |
| save | BOOKMARK | +0.80 | **да** |
| share | BOOKMARK | +0.80 | **да** |
| hide | DISLIKE | -0.70 | **да** |
| scroll_past | IMPRESSION SKIP | -0.05 | нет |

**Обработка:**
1. Загрузка профиля (создание пустого если нет)
2. Классификация сигналов → EMA-обновление профиля
3. Persist entity_interests (upsert + decay + cleanup)
4. Запись в `user_interactions` с `processed=true`
5. Если есть сильные сигналы → Redis debounce → Kafka publish `recommendations.updated`

---

## 5. Kafka — исходящие события

### 5.1 recommendations.updated

| Параметр | Значение |
|----------|----------|
| Topic | `recommendations.updated` |
| Key | user_id |
| Partitions | 12 |
| Consumer | feed-service (будущий) |

```json
{
  "event_type": "recommendations.updated",
  "user_id": "UUID",
  "reason": "onboarding_complete|interactions_processed|manual",
  "timestamp": "ISO8601"
}
```

**Когда публикуется:**

| Триггер | reason | Debounce |
|---------|--------|----------|
| POST /onboarding завершён | `onboarding_complete` | нет — всегда сразу |
| Batch с сильным сигналом (click/save/share/hide) | `interactions_processed` | **15 минут** (Redis TTL 900s) |

**Ожидаемая реакция feed-service:** `DEL feed:user:{userId}` в Redis → следующий запрос ленты вызовет POST /recommendations.

---

## 6. Пайплайн генерации ленты (POST /recommendations)

```
Вход: user_id, count

GetRecommendationsUseCase:
  1. Загрузка rec_profiles → 404 если нет
  2. Загрузка recommendation_history → set[UUID] уже показанных

GenerateFeedUseCase (внутренний):
  Step 0: Инлайн обработка unprocessed user_interactions (EMA)
  Step 1: Двухэтапная выборка кандидатов
    A) 500 по свежести (ORDER BY processed_at DESC)
    B) 500 по embedding similarity (pgvector HNSW <=>)
    Дедупликация по post_id
  Step 2: Dedup-кластеризация
    EXACT/DUPLICATE → Union-Find кластеры → 1 на кластер (самый свежий)
  Step 3: Скоринг (6-компонентная формула, см. секцию 7)
  Step 4: Sort by score DESC
  Step 5: Diversity filter
    Topic streak ≤ 3, global cap ≤ 40%, RELATED spacing gap ≥ 3
  Step 6: Маппинг raw_content.id → published_content.id

GetRecommendationsUseCase (продолжение):
  3. Фильтрация: убрать items из recommendation_history
  4. Обрезка scores → только ordered UUID
  5. Запись items в recommendation_history (ON CONFLICT DO NOTHING)
  6. Возврат {user_id, items, count, generated_at}
```

---

## 7. Формула скоринга

```
score = 0.30 × topic_match
      + 0.25 × embedding_sim
      + 0.15 × entity_match
      + 0.05 × sentiment_match
      + 0.15 × freshness
      + 0.10 × format_match
```

| Компонент | Формула | Диапазон |
|-----------|---------|----------|
| topic_match | `Σ(profile.topic_vector[t] × post.topic_score[t])` для top-3 | [0, 1] |
| embedding_sim | `max(0, cosine(profile.embedding, post.embedding))` | [0, 1] |
| entity_match | `min(count(post.entities ∩ user.entity_interests) / 3, 1)` | [0, 1] |
| sentiment_match | `profile.sentiment_prefs[post.sentiment]` | [0, 1] |
| freshness | `exp(-0.693 × hours / 48)` | [0, 1] |
| format_match | `length_match × complexity_match` | [0, 1] |

**Cold start перераспределение:** Если embedding=null или entity_interests пуст → вес "мёртвого" компонента перераспределяется пропорционально между активными. Сумма весов всегда = 1.0.

---

## 8. Профиль пользователя и EMA

### Создание профиля

| Момент | Что происходит |
|--------|---------------|
| `user.created` Kafka | Пустой профиль: zero vector, uniform topics, cold_start=true |
| POST /onboarding | Warm профиль: topic_vector из категорий, опц. embedding из контента, cold_start=false |

### EMA-обновление (lr = 0.08)

При каждом взаимодействии с weight ≠ 0:

- **topic_vector:** `+= lr × weight × post.topic_score[t]` для top-3, L1-нормализация
- **embedding:** `(1-α)×old + α×post.emb`, α=lr×|weight|, L2-нормализация (только weight > 0)
- **sentiment_prefs:** `+= lr × weight × 0.5` для post.sentiment, нормализация
- **format_prefs:** EMA на word_count и complexity (только weight > 0)
- **entity_interests:** при weight ≥ 0.4 → UPSERT weight+=1, decay ×0.95, cleanup >30 дней

---

## 9. Redis

| Ключ | Тип | TTL | Когда создаётся | Когда читается |
|------|-----|-----|-----------------|----------------|
| `rec:cold-start:trending` | String(JSON) | 3600s | APScheduler job при старте + каждые 60 мин; auto-populate при GET /cold-start cache miss | GET /cold-start |
| `rec:invalidation:last:{user_id}` | String("1") | 900s | После публикации `recommendations.updated` с reason=interactions_processed | Перед публикацией (проверка debounce) |

---

## 10. Background Jobs (APScheduler)

| Job | Интервал | Описание |
|-----|----------|----------|
| Content Processing | 5 мин | raw_content (COMPLETED, NOT is_processed_by_rec) → NLP → posts_features + flag |
| Profile Update | 5 мин | user_interactions (processed=false) → classify → EMA → mark processed |
| Cold-Start Refresh | 60 мин + при старте | 50 newest published_content → JSON → Redis |
| History Cleanup | 24 часа | DELETE recommendation_history WHERE recommended_at < 30 дней |

---

## 11. NLP-пайплайн

Для каждого необработанного поста из raw_content:

| Шаг | Модель | Вход | Выход |
|-----|--------|------|-------|
| 1. Text | — | title + content (truncate 2000 chars) | nlp_text |
| 2. Topics | rubert-base-cased-nli-threeway (zero-shot) | nlp_text + 18 candidate labels | top-3 (topic, score) |
| 3. Sentiment | rubert-base-cased-sentiment | nlp_text | POS/NEG/NEU + score |
| 4. NER | spaCy ru_core_news_lg | full text | persons, orgs, locations |
| 5. Embedding | rubert-tiny2 (SentenceTransformer) | nlp_text | 312-dim L2-normalized |
| 6. Metrics | textstat + custom | full text | word_count, complexity, reading_time, is_short/long |

---

## 12. Таблицы (data_flow schema)

### Owned (rec-system пишет)

#### posts_features
NLP-фичи контента. PK: `post_id` UUID.  
FK: `post_id → raw_content.id ON DELETE CASCADE`

| Колонка | Тип | Описание |
|---------|-----|----------|
| post_id | UUID PK | = raw_content.id |
| topic_1..3 + score | VARCHAR + REAL | Top-3 NLI topic |
| sentiment + score | VARCHAR + REAL | POS/NEG/NEU |
| entities_persons/orgs/locations | JSONB | NER |
| embedding | vector(312) | rubert-tiny2 |
| text_length, word_count, complexity, reading_time | int/real | Метрики |
| is_short_form, is_long_form | bool | <50 слов / ≥300 слов |

#### rec_profiles
Профили пользователей. PK: `user_id` UUID.

| Колонка | Тип | Описание |
|---------|-----|----------|
| user_id | UUID PK | |
| topic_vector | JSONB | 18 ключей, L1-normalized, сумма=1 |
| embedding | vector(312) | Усреднённый embedding интересов |
| sentiment_prefs | JSONB | {POSITIVE, NEGATIVE, NEUTRAL} |
| format_prefs | JSONB | {avg_length, avg_complexity} |
| interaction_count | int | Обработанных событий |
| cold_start | bool | true→false после онбординга |

#### rec_entity_interests
Интересы к NER-сущностям. PK: (user_id, entity_type, entity_name).  
FK: `user_id → rec_profiles.user_id ON DELETE CASCADE`

#### rec_config
Key-value конфигурация. PK: `key` VARCHAR.  
Ключи: signal_weights, scoring_weights, profile_params, ranking_params, onboarding_params, dedup_params.

#### categories
Справочник 18 NLP-категорий. PK: `id` VARCHAR.  
Единый источник: GET /categories, POST /onboarding, TopicVector, NLI classifier, скоринг.

#### recommendation_history
Что уже рекомендовали. PK: (user_id, content_id).  
FK: `user_id → rec_profiles.user_id ON DELETE CASCADE`  
FK: `content_id → published_content.id ON DELETE CASCADE`  
Cleanup: APScheduler удаляет >30 дней.

#### user_interactions
Записи из Kafka batches. PK: `id` BIGSERIAL. UNIQUE: `event_id` UUID.  
Kafka-sourced записи сохраняются с `processed=true` (EMA уже применён).

### Cross-system reads (rec-system только читает)

| Таблица | Owner | Что делает rec-system |
|---------|-------|----------------------|
| raw_content | parser | Polls WHERE COMPLETED AND NOT is_processed_by_rec; UPDATE flag |
| articles | dedup | JOIN для dedup clustering |
| similarities | dedup | EXACT/DUPLICATE → clustering, RELATED → spacing |
| published_content | parser | Маппинг raw→published ID; cold-start list |

### ER-диаграмма (FK связи)

```
config.sources (cross-schema, no FK)
    │
    ▼ source_id (no FK — cross-schema)
raw_content ──────────────────────────────────────────────
    │ PK: id                                               
    ├──→ articles.raw_content_id (FK CASCADE)       [dedup]
    ├──→ posts_features.post_id (FK CASCADE)        [rec]  
    └──→ published_content.content_id (FK CASCADE)  [parser]
                │ PK: id
                └──→ recommendation_history.content_id (FK CASCADE) [rec]

rec_profiles ─────────────────────────
    │ PK: user_id
    ├──→ rec_entity_interests.user_id (FK CASCADE)
    └──→ recommendation_history.user_id (FK CASCADE)

articles ──────────────
    │ PK: id
    ├──→ similarities.article_a (FK NO ACTION)
    └──→ similarities.article_b (FK NO ACTION)
```

---

## 13. Переменные окружения

| Переменная | Default | Обязательность |
|-----------|---------|---------------|
| DATABASE_URL | postgresql+asyncpg://...localhost:5432/content_agg_db | обязательна |
| REDIS_URL | redis://localhost:6379/0 | обязательна |
| KAFKA_BOOTSTRAP_SERVERS | localhost:9092 | обязательна |
| APP_VERSION | 0.1.0 | нет |
| COLD_START_REFRESH_MINUTES | 60 | нет |
| HISTORY_CLEANUP_DAYS | 30 | нет |

---

## 14. Взаимодействие с feed-service (ожидаемый контракт)

feed-service — будущий сервис, который управляет лентой пользователя. Ожидаемый паттерн работы:

### Получение ленты пользователя

```
1. Пользователь открывает ленту → GET /feed
2. feed-service проверяет Redis: LRANGE feed:user:{userId} 0 19
3. Cache hit → возвращает первые 20 ID
4. Cache miss → POST /recommendations {user_id, count: 120} к rec-system
5. rec-system возвращает 120 ordered UUIDs
6. feed-service: RPUSH feed:user:{userId} ...120 IDs, EXPIRE 1800 (30 мин)
7. feed-service возвращает первые 20 ID
```

### Скролл (пагинация)

```
1. Пользователь скроллит → GET /feed?offset=20
2. feed-service: LRANGE feed:user:{userId} 20 39
3. Возвращает следующие 20 ID из кеша
4. Если offset > 50% от cached → prefetch: POST /recommendations {count: 120}
```

### Инвалидация кеша

```
1. rec-system → Kafka: recommendations.updated {user_id, reason}
2. feed-service consumer: DEL feed:user:{userId}
3. Следующий GET /feed → cache miss → POST /recommendations → свежая лента
```

### Cold-start (новый пользователь)

```
1. POST /recommendations → 404 (профиль не создан)
2. feed-service → GET /recommendations/cold-start?count=30
3. Возвращает trending контент (без персонализации)
```
