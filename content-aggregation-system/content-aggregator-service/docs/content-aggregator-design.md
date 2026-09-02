# Дизайн-документ: content-aggregator-service (агрегатор контента)

**Дата:** 2026-04-04  
**Стек:** Kotlin / Spring Boot 3.4.5 / Spring Data JDBC / PostgreSQL  
**Порт:** 8086  

---

## 1. Назначение

content-aggregator-service — read-only REST API для чтения опубликованного контента из `data_flow.published_content`. Не пишет данные, не использует Kafka. Является источником контента для feed-service и фронтенда.

---

## 2. Зависимости

| Сервис | Назначение | Обязательно |
|--------|------------|-------------|
| PostgreSQL | Чтение published_content, articles, similarities | да |

**Не используется:** Kafka, Redis, MinIO. Чисто read-only сервис.

---

## 3. HTTP API — контракты

Base path: `/api/v1/content`

### 3.1 POST /api/v1/content/batch

**Основной эндпоинт для feed-service.** Возвращает контент для отрисовки на фронте с опциональными связанными постами.

```json
// Request
POST /api/v1/content/batch
{
  "ids": ["UUID", "UUID", ...],
  "include_related": true,
  "related_limit": 5
}
```

| Поле | Тип | Required | Default | Валидация |
|------|-----|----------|---------|-----------|
| ids | list[UUID] | да | — | max 100 |
| include_related | boolean | нет | false | |
| related_limit | int | нет | 5 | max 10 |

```json
// Response 200
{
  "items": {
    "uuid-1": {
      "id": "uuid-1",
      "title": "Заголовок статьи",
      "description": "Краткое описание или null",
      "content": "<p>Полный HTML с S3 URLs для медиа...</p>",
      "content_format": "HTML",
      "source_type": "HABR",
      "source_subtype": "company",
      "url": "https://habr.com/...",
      "published_at": "2026-04-03T12:00:00Z",
      "author_name": "username",
      "media": [
        {"type": "image", "url": "https://s3.../img.jpg", "width": 800, "height": 600}
      ],
      "metadata": {},
      "related_ids": ["uuid-5", "uuid-8"]
    },
    "uuid-2": {
      "id": "uuid-2",
      "title": "...",
      "...": "...",
      "related_ids": []
    }
  },
  "not_found": ["uuid-99"]
}
```

**Структура ответа:**

- `items` — **Map\<String, ContentBatchItem\>**, ключ = published_content.id. Формат map для O(1) lookup на фронте
- `not_found` — список ID которых нет в БД (всегда присутствует, даже если пуст)
- Дупликаты в `ids` дедуплицируются

**Поля ContentBatchItem (для фронтенда):**

| Поле | Тип | Описание |
|------|-----|----------|
| id | String | published_content.id |
| title | String? | Заголовок (null для Telegram без заголовка) |
| description | String? | Краткое описание |
| content | String? | Полный HTML с S3 URLs для медиа |
| content_format | String | "HTML" (всегда) |
| source_type | String | HABR, VCRU, TELEGRAM_CHANNEL |
| source_subtype | String? | company, user, hub, channel |
| url | String? | Ссылка на оригинал |
| published_at | Instant? | Дата публикации в источнике |
| author_name | String? | Автор |
| media | List\<Map\> | Медиа-объекты (type, url, width, height) |
| metadata | Map | Доп. key-value данные |
| related_ids | List\<String\>? | Связанные посты (null если include_related=false) |

**Исключённые поля (внутренние, не нужны фронтенду):**
- content_id (raw_content.id)
- external_id (ID из внешнего источника)
- source_id (UUID из config.sources)
- dedup_article_id (внутренний ID dedup)
- content_hash (SHA-256 для dedup)
- created_at, updated_at (служебные timestamps)

**Логика related_ids:**

Для каждого поста с `dedup_article_id != null`:
1. Запрос `similarities` WHERE (article_a = id OR article_b = id) AND rel_type IN ('RELATED', 'DUPLICATE')
2. JOIN на `articles` (другая сторона) → JOIN на `published_content` (маппинг на published ID)
3. Фильтрация: только опубликованные посты, исключить сам себя
4. Сортировка по score DESC, limit = `related_limit`
5. Двунаправленный: работает независимо от того, article_a или article_b

```sql
-- SQL для related content (batch для всех запрошенных article_ids)
SELECT DISTINCT ON (source_article_id, pc_related.id)
    CASE WHEN s.article_a = ANY(:articleIds) THEN s.article_a ELSE s.article_b END AS source_article_id,
    pc_related.id AS related_id,
    s.score
FROM data_flow.similarities s
JOIN data_flow.articles a_other
    ON a_other.id = CASE
        WHEN s.article_a = ANY(:articleIds) THEN s.article_b
        ELSE s.article_a
    END
JOIN data_flow.published_content pc_related
    ON pc_related.content_id = a_other.raw_content_id
WHERE (s.article_a = ANY(:articleIds) OR s.article_b = ANY(:articleIds))
  AND s.rel_type IN ('RELATED', 'DUPLICATE')
ORDER BY source_article_id, pc_related.id, s.score DESC
```

---

### 3.2 GET /api/v1/content/{id}

Одиночный контент по ID.

```json
// Response 200 — ContentResponse (полный, с internal полями)
{
  "id": "uuid",
  "content_id": "uuid",
  "external_id": "...",
  "title": "...",
  "content": "...",
  "source_type": "HABR",
  "...": "...",
  "dedup_article_id": 66,
  "content_hash": "sha256...",
  "created_at": "...",
  "updated_at": "..."
}

// Response 404
```

**Примечание:** Этот endpoint возвращает полный `ContentResponse` (включая internal поля). Для фронтенда рекомендуется использовать `POST /batch`.

---

### 3.3 GET /api/v1/content

Поиск контента с фильтрами и пагинацией.

```
GET /api/v1/content?search=kotlin&sourceType=HABR&page=0&size=20&sortBy=publishedAt&sortDirection=DESC
```

| Параметр | Тип | Default | Описание |
|----------|-----|---------|----------|
| sourceType | enum | null | HABR, VCRU, TELEGRAM_CHANNEL |
| sourceId | String | null | UUID источника |
| search | String | null | ILIKE по title и description |
| fromDate / toDate | ISO8601 | null | Диапазон дат |
| page | int | 0 | Номер страницы (0-based) |
| size | int | 20 | Размер страницы (max 100) |
| sortBy | String | publishedAt | Поле сортировки |
| sortDirection | String | DESC | ASC или DESC |

```json
// Response 200
{
  "content": [ContentResponse, ...],
  "page_number": 0,
  "page_size": 20,
  "total_elements": 150,
  "total_pages": 8,
  "first": true,
  "last": false
}
```

---

### 3.4 GET /api/v1/content/latest

```
GET /api/v1/content/latest?page=0&size=20
```

Возвращает контент по `published_at DESC NULLS LAST`.

---

### 3.5 GET /api/v1/content/by-type/{sourceType}

```
GET /api/v1/content/by-type/HABR?page=0&size=20
```

Фильтрация по типу источника.

---

## 4. Таблицы (data_flow schema)

### Таблицы которые читает aggregator-service

#### published_content (основная)

**Owner:** content-parser-service  
**aggregator:** SELECT only

| Колонка | Тип | Описание |
|---------|-----|----------|
| id | UUID PK | Публичный ID контента |
| content_id | UUID NOT NULL | FK → raw_content.id (ON DELETE CASCADE) |
| external_id | VARCHAR NOT NULL | ID из внешнего источника |
| title | VARCHAR(500) | Заголовок (null для Telegram) |
| description | TEXT | Описание |
| content | TEXT | Полный HTML с S3 URLs |
| content_format | VARCHAR | "HTML" |
| source_id | UUID NOT NULL | FK (логический) → config.sources.id |
| source_type | VARCHAR NOT NULL | HABR, VCRU, TELEGRAM_CHANNEL |
| source_subtype | VARCHAR | company, user, hub, channel |
| url | VARCHAR | Ссылка на оригинал |
| published_at | TIMESTAMPTZ | Дата в источнике |
| author_id / author_name | VARCHAR | Автор |
| media | JSONB | [{type, url, width, height}] |
| metadata | JSONB | Доп. данные |
| dedup_article_id | BIGINT | FK (логический) → articles.id |
| content_hash | VARCHAR(64) | SHA-256 |
| created_at / updated_at | TIMESTAMPTZ | Timestamps |

**Индексы:** published_at DESC, created_at DESC, source_type, (source_type, published_at DESC), source_id, content_id. UNIQUE(source_type, external_id).

#### articles (для related content)

**Owner:** dedup-system  
**aggregator:** SELECT only (через RelatedContentService)

| Колонка | Тип | Описание |
|---------|-----|----------|
| id | BIGSERIAL PK | |
| raw_content_id | UUID NOT NULL UNIQUE | FK → raw_content.id (ON DELETE CASCADE) |
| content_hash | TEXT | SHA-256 |
| embedding | vector(1024) | BGE-M3 |

#### similarities (для related content)

**Owner:** dedup-system  
**aggregator:** SELECT only (через RelatedContentService)

| Колонка | Тип | Описание |
|---------|-----|----------|
| article_a | BIGINT | FK → articles.id, всегда < article_b |
| article_b | BIGINT | FK → articles.id |
| score | REAL | Cosine similarity [0,1] |
| rel_type | TEXT | EXACT, DUPLICATE, RELATED |

### ER-диаграмма (что читает aggregator)

```
published_content ←── content_id ──→ raw_content
    │ dedup_article_id
    ▼ (логический, no FK)
articles ←─── article_a/b ───→ similarities
    │ raw_content_id
    ▼ (FK CASCADE)
raw_content
```

**Путь для related_ids:**
```
published_content.dedup_article_id 
  → articles.id 
  → similarities (обе стороны) 
  → articles (другая сторона) 
  → published_content (через raw_content_id = content_id)
```

---

## 5. Взаимодействие с feed-service (ожидаемый контракт)

### Роль content-aggregator-service

CAS — **stateless read-only API**. Не кеширует (Caffeine удалён). Кеширование контента управляется **feed-service через Redis**.

### Ожидаемая архитектура кеширования

```
Frontend → feed-service → Redis (cache) → CAS (cache miss) → PostgreSQL
                ↑                              │
                └──── populate cache ──────────┘
```

### Redis-ключи для контента (управляет feed-service)

| Ключ | Тип | TTL | Описание |
|------|-----|-----|----------|
| `content:{published_content_id}` | String(JSON) | 24h | Полный ContentBatchItem |
| `related:{published_content_id}` | String(JSON) | 2h | List\<UUID\> related IDs |
| `content:miss:{published_content_id}` | String("1") | 5min | Negative cache (ID не найден) |

### Алгоритм feed-service при запросе контента

```
1. Получить ленту: POST /recommendations → [id1, id2, ..., id120]

2. Проверить Redis кеш:
   MGET content:{id1} content:{id2} ... content:{id120}
   
3. Разделить:
   cached[]   = IDs с данными в Redis
   uncached[] = IDs без данных
   
4. Проверить negative cache для uncached:
   MGET content:miss:{id} для каждого uncached
   → Убрать known-missing из запроса

5. Запросить CAS для оставшихся:
   POST /api/v1/content/batch {
     ids: uncached (без known-missing),
     include_related: true,
     related_limit: 5
   }
   
6. Обработать ответ CAS:
   Для каждого item в response.items:
     SET content:{id} JSON EX 86400      (24 часа)
     SET related:{id} JSON EX 7200       (2 часа)
   Для каждого id в response.not_found:
     SET content:miss:{id} "1" EX 300    (5 минут)

7. Вернуть клиенту: merge(cached, freshly_fetched)
```

### Инвалидация кеша контента

| Событие | Действие feed-service | Триггер |
|---------|----------------------|---------|
| Новый контент опубликован | DEL related:{id} для связанных | Опционально: Kafka `content.published` (будущий) |
| Контент обновлён | DEL content:{id} | Опционально: Kafka `content.updated` (будущий) |
| TTL истёк (24h) | Автоматически | Redis TTL |

На текущем этапе инвалидация через TTL достаточна — контент редко меняется после публикации. Related-связи обновляются через TTL 2h (новые связи появляются при парсинге).

### Пагинация ленты (фронтенд → feed-service)

```
Первый экран:
  feed-service: LRANGE feed:user:{userId} 0 19 → 20 IDs
  feed-service: проверяет Redis content:{id} для каждого → cache hit
  → Возвращает 20 ContentBatchItem

Скролл:
  feed-service: LRANGE feed:user:{userId} 20 39 → 20 IDs
  feed-service: content:{id} → уже в кеше (загружены при первом batch запросе)
  → Мгновенный ответ
  
Prefetch (offset > 50%):
  feed-service: POST /recommendations {count: 120} → новый batch
```

---

## 6. Source Types

| source_type | Описание | source_subtype |
|-------------|----------|---------------|
| HABR | Статьи с habr.com | company, user, hub |
| VCRU | Статьи с vc.ru | USER, COMPANY |
| TELEGRAM_CHANNEL | Посты из Telegram-каналов | null |
| RSS | RSS-ленты (не реализован) | null |

---

## 7. Переменные окружения

| Переменная | Default | Описание |
|-----------|---------|----------|
| SERVER_PORT | 8086 | Порт сервиса |
| DB_HOST | localhost | PostgreSQL host |
| DB_PORT | 5432 | PostgreSQL port |
| DB_NAME | content_agg_db | База данных |
| DB_SCHEMA | data_flow | Схема |
| DB_USER | postgres | Пользователь |
| DB_PASSWORD | postgres | Пароль |
