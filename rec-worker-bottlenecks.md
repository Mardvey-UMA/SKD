# rec-worker — анализ узких мест NLP pipeline

**Дата**: 2026-04-18
**Текущий throughput**: ~4 поста/мин (20 постов / 5-минутный тик)
**Цель**: 25–50 постов/мин
**Источник данных для замеров**:
- `kubectl exec -n skd postgres-0 -- psql … "SELECT date_trunc('minute', processed_at), COUNT(*) FROM data_flow.posts_features WHERE processed_at > NOW() - INTERVAL '10 minutes' GROUP BY 1"`
- Логи контейнера: `docker logs ml-rec-worker --tail 200`
- Исходники: `/home/mattew/SKD/rec-system/src/`

---

## 1. Как сейчас устроен rec-worker

### 1.1 Общая схема

```
            ┌────────────────────────────┐
            │ APScheduler (in-memory)    │
            │ every 5 min → content_proc │
            └──────────────┬─────────────┘
                           │  batch_size = env REC_CONTENT_BATCH_SIZE (default 20)
                           ▼
            ┌────────────────────────────────────────────┐
            │ ProcessContentUseCase.execute()            │
            │                                            │
            │   posts = repo.get_unprocessed(20)         │
            │   for post in posts:       ← serial loop   │
            │     _process_single(post)                  │
            │     repo.save_features(features)           │
            └──────────────┬─────────────────────────────┘
                           │  20 раз по 1 посту
                           ▼
           ┌──────────────────────────────────────────┐
           │ _process_single(post):                   │
           │   topics     = await topic.classify()    │ ~2–3 сек (GPU)
           │   sentiment  = await sent.analyze()      │ ~150–250 мс (GPU)
           │   entities   = await ner.extract()       │ ~100–300 мс (CPU spaCy)
           │   embedding  = await enc.encode()        │ ~30–80 мс (GPU)
           │   metrics    = text.analyze()            │ ~1 мс (Python pure)
           │   repo.save_features(...)                │ ~30–80 мс (DB roundtrip)
           └──────────────────────────────────────────┘
```

### 1.2 Ключевые файлы

| Файл | Роль |
|------|------|
| `src/infrastructure/scheduling/job_scheduler.py` | APScheduler, запускает `_run_content_processing` каждые 5 мин с `REC_CONTENT_BATCH_SIZE` (default 20) |
| `src/application/use_cases/process_content.py` | **Узкое место — serial for-loop** (строки 72–103) |
| `src/infrastructure/nlp/torch_topic_classifier.py` | Zero-shot NLI, 18 категорий. Вызывает `pipeline()` на ОДНОМ тексте (batch=1) |
| `src/infrastructure/nlp/torch_sentiment_analyzer.py` | 3-class sentiment BERT. Вызывает `pipeline()` на ОДНОМ тексте (batch=1) |
| `src/infrastructure/nlp/torch_content_encoder.py` | rubert-tiny2 SentenceTransformer. Уже имеет `encode_batch()`, но **use-case не использует** |
| `src/infrastructure/nlp/spacy_entity_extractor.py` | spaCy NER на CPU. Через `asyncio.to_thread` |
| `src/infrastructure/persistence/pg_content_repository.py` | `save_features` — INSERT по одному посту |

### 1.3 Модели и батчи, задекларированные в коде

| Компонент | Модель | `batch_size` поле | Реально используется? |
|---|---|---|---|
| TorchTopicClassifier | `cointegrated/rubert-base-cased-nli-threeway` | **32** | ❌ (вызывается с одним текстом) |
| TorchSentimentAnalyzer | `blanchefort/rubert-base-cased-sentiment` | **128** | ❌ |
| TorchContentEncoder | `cointegrated/rubert-tiny2` (SentenceTransformer) | **32** | ❌ `encode()` зовётся с 1 текстом |
| SpacyEntityExtractor | `ru_core_news_lg` | — | N/A (CPU, NLP без батча) |

**Все адаптеры загружают модели как singletons** через `dependency-injector` (`container.py`) — это сделано правильно, модели НЕ перезагружаются на каждый пост.

---

## 2. Узкие места

### 🔴 БМ-1. Serial for-loop в `ProcessContentUseCase` — **главный тормоз**

**Файл**: `src/application/use_cases/process_content.py:72-103`

```python
for post in posts:
    try:
        async with asyncio.timeout(per_post_timeout):
            features = await self._process_single(post)   # ← 5 forward passes на GPU для ОДНОГО поста
    except TimeoutError:
        ...
    await self._content_repo.save_features(features)       # ← один INSERT, ждём
    processed_count += 1
```

**Почему тормозит**:

1. GPU простаивает 80% времени. RTX 5060 Ti может обрабатывать batch=32 почти за то же время что batch=1 — стоимость forward pass доминирует стоимостью scheduler'а и копирования весов в L2 cache. При batch=1 мы теряем весь GPU parallelism.
2. Четыре отдельных forward pass на пост (topic, sentiment, encoder — GPU; NER — CPU). Никакой перекрытия — всё sequential.
3. `await asyncio.to_thread(...)` в каждом адаптере означает выход в event loop и обратно 4 раза на пост → ~2–4 мс overhead, на 20 постов ×4 = 160–320 мс чистой «проходящей» задержки.

**Измеренный эффект**: 20 постов × ~4–5 сек = 80–100 сек на тик при 5-минутном scheduler → 4 поста/мин.

**Решение — batch всю пачку одним forward pass'ом**:

```python
# process_content.py — новая структура
async def execute(self, command: ProcessContentCommand) -> ProcessContentResult:
    posts = await self._content_repo.get_unprocessed(command.batch_size)
    if not posts:
        return ProcessContentResult(processed_count=0)

    # 1. Готовим все тексты (CPU, быстро)
    nlp_texts: list[str] = []
    bodies: list[str] = []
    for post in posts:
        title = post.title or ""
        body = (post.content or "")[: self._truncate_text_bytes]
        full_text = prepend_title(title, body).strip()
        nlp_text, _ = extract_hmt(full_text, self._content_encoder.tokenizer, self._max_nlp_tokens)
        nlp_texts.append(nlp_text)
        bodies.append(body)

    # 2. Один GPU forward pass на всю пачку в каждой модели
    topics_batch     = await self._topic_classifier.classify_batch(nlp_texts)      # new method
    sentiments_batch = await self._sentiment_analyzer.analyze_batch(nlp_texts)     # new method
    embeddings_batch = await self._content_encoder.encode_batch(nlp_texts)         # уже есть!

    # 3. spaCy NER — CPU, batch через nlp.pipe() (см. БМ-2)
    entities_batch = await self._entity_extractor.extract_batch(bodies)            # new method

    # 4. Сборка features + bulk save
    features_list: list[ContentFeatures] = []
    for post, topics, sent, emb, ent, body in zip(
        posts, topics_batch, sentiments_batch, embeddings_batch, entities_batch, bodies
    ):
        tm = self._text_analyzer.analyze(body)
        features_list.append(ContentFeatures(
            post_id=post.post_id,
            topic_1=topics[0][0] if topics else None,
            topic_1_score=topics[0][1] if topics else None,
            # ... sentiment, embedding, entities, metrics ...
        ))

    await self._content_repo.save_features_bulk(features_list)       # см. БМ-6
    return ProcessContentResult(processed_count=len(features_list))
```

HuggingFace `pipeline()` уже нативно принимает `list[str]` — в адаптерах нужно только добавить batch-метод:

```python
# torch_topic_classifier.py — добавить метод
async def classify_batch(self, texts: list[str]) -> list[list[tuple[str, float]]]:
    if not texts:
        return []
    # Pipeline принимает list и возвращает list
    results = await asyncio.to_thread(self._classify_batch_sync, texts)
    output: list[list[tuple[str, float]]] = []
    for result in results:
        labels = result["labels"][:TOP_K]
        scores = result["scores"][:TOP_K]
        output.append([(label, round(float(score), 4)) for label, score in zip(labels, scores)])
    return output

@torch.no_grad()
def _classify_batch_sync(self, texts: list[str]) -> list[dict]:
    return self._pipe(
        texts,                              # ← list вместо str
        candidate_labels=TOPICS,
        hypothesis_template=HYPOTHESIS_TEMPLATE,
        multi_label=False,
        truncation=True,
        max_length=512,
        batch_size=self._batch_size,        # ← pipeline использует поле
    )
```

**Ожидаемый эффект**: 4× → 20× ускорение (зависит от VRAM-headroom для batch_size). Самый большой единичный win.

---

### 🔴 БМ-2. `pipeline()` на одном тексте вместо `nlp.pipe()` для spaCy

**Файл**: `src/infrastructure/nlp/spacy_entity_extractor.py:73`

```python
doc = await asyncio.to_thread(self._nlp, text[:MAX_TEXT_LENGTH])   # один документ
```

spaCy имеет специальный API `nlp.pipe(texts)` который:
- Параллелит tokenization / parser / NER на multiple CPU cores
- Переиспользует буферы, снижает Python overhead на каждый doc

**Почему тормозит**: каждый вызов `self._nlp(text)` — отдельный Python call + full spaCy stack setup ≈ 50–100 мс overhead. Для 20 постов = 1–2 сек чистого overhead.

**Решение**:

```python
# spacy_entity_extractor.py — добавить batch
async def extract_batch(self, texts: list[str]) -> list[dict[str, list[str]]]:
    if not texts:
        return []
    truncated = [(t or "")[:MAX_TEXT_LENGTH] for t in texts]
    # nlp.pipe() — spaCy native batching, parallel under n_process=1 but optimized Python
    docs = await asyncio.to_thread(
        lambda: list(self._nlp.pipe(truncated, batch_size=16))
    )
    return [self._doc_to_entities(doc) for doc in docs]

def _doc_to_entities(self, doc) -> dict[str, list[str]]:
    result = {"persons": [], "organizations": [], "locations": []}
    seen = {k: set() for k in result}
    for ent in doc.ents:
        category = _LABEL_MAP.get(ent.label_)
        if not category:
            continue
        entity_text = ent.text.strip()
        if not _is_valid_entity(entity_text) or entity_text in seen[category]:
            continue
        seen[category].add(entity_text)
        result[category].append(entity_text)
    return result
```

**Ожидаемый эффект**: 2–3× ускорение NER (с 300 мс до 100 мс на пост в среднем).

---

### 🟡 БМ-3. `encode_batch` уже есть — но use-case его не использует

**Файл**: `src/infrastructure/nlp/torch_content_encoder.py:44-47` — метод уже есть.

```python
async def encode_batch(self, texts: list[str]) -> list[list[float]]:
    embeddings = await asyncio.to_thread(self._encode_sync, texts, self._batch_size)
    return [emb.tolist() for emb in embeddings]
```

Но в `process_content.py:164`:

```python
embedding = await self._content_encoder.encode(nlp_text)   # ← ОДИН текст, batch_size=1!
```

Тот же код уже бы работал быстрее, если бы собирали `nlp_text` в список и звали `encode_batch`. Это даже не требует менять интерфейс.

**Решение**: покрывается БМ-1.

**Ожидаемый эффект**: 10× ускорение encode step. Поскольку encode — всего ~5% времени на пост, это даст только ~5% общего ускорения самостоятельно, но бесплатно идёт с БМ-1.

---

### 🟡 БМ-4. `asyncio.to_thread` каждый раз выделяет thread из пула

**Файл**: все `torch_*.py`

```python
result = await asyncio.to_thread(self._classify_sync, text)
```

`asyncio.to_thread` использует default ThreadPoolExecutor с `min(32, os.cpu_count() + 4)` потоков. Каждый вызов — capture текущего контекста + lock + switch. Для CPU-bound спаси где GIL удерживается основную часть времени это норм. Но для четырёх последовательных вызовов на пост × 20 постов = 80 переключений контекста.

**Почему тормозит**: ~0.5–1 мс overhead на вызов, 80 вызовов = 40–80 мс на тик. Небольшое, но лечится тривиально.

**Решение**: после БМ-1 у нас будет 4 вызова `to_thread` на пачку из 20 постов (вместо 80). Проблема сама исчезает.

**Ожидаемый эффект**: покрывается БМ-1, самостоятельно 2–3%.

---

### 🟡 БМ-5. GPU операции сериализуются через единственный thread → нет overlap topic ↔ sentiment ↔ embedding

**Файлы**: `torch_topic_classifier.py`, `torch_sentiment_analyzer.py`, `torch_content_encoder.py`

PyTorch держит single default CUDA stream. Все три модели кладут работу в один stream. Даже если мы запустим:

```python
topics, sentiment, embedding = await asyncio.gather(
    self._topic_classifier.classify(t),
    self._sentiment_analyzer.analyze(t),
    self._content_encoder.encode(t),
)
```

они всё равно выполнятся серийно в GPU. Но **CPU part** (tokenizer, argmax, copy to numpy) МОЖЕТ пересекаться с GPU forward pass следующей модели.

**Почему тормозит**: без CUDA streams — overlap GPU/CPU парт не происходит. Точка экономии 15–25%.

**Решение (опционально, после БМ-1)**:
1. `asyncio.gather()` в `_process_batch` — легко, но gains только от NER (CPU) перекрывающихся с GPU. Остальное — serial на default stream.
2. Явные CUDA streams для параллели topic+sentiment+embedding на GPU:

```python
# torch_topic_classifier.py
def __init__(self, ..., cuda_stream=None):
    self._stream = cuda_stream or torch.cuda.Stream()

@torch.no_grad()
def _classify_batch_sync(self, texts):
    with torch.cuda.stream(self._stream):
        return self._pipe(texts, ...)
```

Сложнее правильно синхронизировать `torch.cuda.synchronize()` и тесты с мокнутым GPU становятся неприятными. **Отложить до после БМ-1+2**, если тех throughput не хватит.

**Ожидаемый эффект**: 10–20% дополнительно поверх БМ-1.

---

### 🟡 БМ-6. `save_features` один пост = один INSERT

**Файл**: `src/infrastructure/persistence/pg_content_repository.py:save_features`

Сейчас:

```python
features_stmt = insert(PostsFeaturesModel).values(post_id=..., ...)
await session.execute(features_stmt)
await session.execute(update_raw_content_stmt)
await session.commit()
# + отдельно save_features для каждого следующего поста
```

**Почему тормозит**: каждый `commit()` — ~10–30 мс fsync на postgres. 20 постов × 30 мс = 0.6 сек на тик только на DB sync.

**Решение** — bulk save в одной транзакции:

```python
# pg_content_repository.py — новый метод
async def save_features_bulk(self, features_list: list[ContentFeatures]) -> None:
    if not features_list:
        return
    async with self._session_factory() as session:
        async with session.begin():
            # Bulk upsert via PostgreSQL ON CONFLICT
            values = [self._features_to_row(f) for f in features_list]
            stmt = (
                insert(PostsFeaturesModel)
                .values(values)
                .on_conflict_do_update(
                    index_elements=[PostsFeaturesModel.post_id],
                    set_={col: getattr(insert(PostsFeaturesModel).excluded, col)
                          for col in FEATURES_UPDATE_COLUMNS},
                )
            )
            await session.execute(stmt)

            # Bulk flag update
            post_ids = [f.post_id for f in features_list]
            await session.execute(
                update(RawContentModel)
                .where(RawContentModel.id.in_(post_ids))
                .values(is_processed_by_rec=True)
            )
        # commit только один раз
```

**Ожидаемый эффект**: 10–20× ускорение DB части (с ~600 мс на тик до ~30 мс). Общего эффекта: 5–8%.

---

### 🟠 БМ-7. Топик-классификатор делает 18 NLI pairs на каждый пост

**Файл**: `src/infrastructure/nlp/torch_topic_classifier.py:55-62`

```python
return self._pipe(
    text,
    candidate_labels=TOPICS,       # ← 18 категорий = 18 (premise, hypothesis) pairs
    hypothesis_template=HYPOTHESIS_TEMPLATE,
    multi_label=False,
    ...
)
```

Zero-shot NLI фундаментально: N категорий = N forward pass BERT'а (каждая пара text↔"Этот текст про X"). 18 категорий = 18 forward pass на один пост.

**Почему тормозит**: это ~70% времени всего pipeline. Не исправить без смены подхода.

**Возможные решения (серьёзные, не в MVP)**:

1. **Multi-label classifier** (fine-tune обычный BERT на 18 outputs): **1 forward pass вместо 18** = 18× ускорение топиков. Нужна labeled датасет или distillation из zero-shot на корпусе.
2. **Embedding-based topic classifier**: pre-encode 18 category descriptions, для поста считать cosine similarity к category embeddings. 1 embedding forward pass + cheap матричное произведение. Качество вероятно близко к zero-shot.
3. **Batch candidate_labels across posts**: не помогает, pipeline уже batch'ит pairs внутри.

**Ожидаемый эффект**: Option 1 даст ~10× ускорение всего pipeline. Но это **неделя работы + перенастройка модели**. Откладываем.

---

### 🔵 БМ-8. FP16/BF16 → INT8 квантизация (долгосрочное)

Сейчас модели в `torch_dtype=torch.bfloat16`. Перевод в INT8:
- `bitsandbytes.nn.Linear8bitLt` — transparent для HF pipeline
- ONNX Runtime + dynamic quantization

**Эффект**: 1.5–2× скорость, 2× меньше VRAM (освободит место, позволит больший batch_size).

**Цена**: 2–3 дня работы + downstream quality tests (на zero-shot NLI падение может быть ощутимым).

---

### 🔵 БМ-9. Одинокий rec-worker, нет horizontal scaling

Сейчас **1 инстанс** rec-worker. Т.к. APScheduler in-memory и нет distributed lock — просто запустить 2 реплики **нельзя** (они оба будут делать ту же работу, race на UPDATE `is_processed_by_rec`).

Требуется:
1. Переделать `get_unprocessed` на `SELECT FOR UPDATE SKIP LOCKED` (как в dedup-worker).
2. Заменить APScheduler на Spring-style `@Scheduled` aналог Python → `asyncio.create_task` с `asyncio.sleep`, в каждом pod независимо.
3. Либо distributed jobstore (Redis/Postgres) для APScheduler.

**Эффект**: линейно от числа реплик. НО — GPU это общий ресурс. На RTX 5060 Ti 16 GB: один rec-worker ≈ 3 GB VRAM, теоретически 4 реплики. На практике — 2 реплики безопасно.

**Цена**: день работы + требует БМ-1 (batch), чтобы было что параллелить.

---

## 3. Сводная таблица: что, сколько, когда

| # | Узкое место | Effort | Ожидаемый gain | Приоритет |
|---|---|---|---|---|
| БМ-1 | Batch в UseCase | 4–6 ч | **4–10×** | 🔴 сразу |
| БМ-2 | spaCy `nlp.pipe()` | 1–2 ч | 1.3× NER | 🔴 в том же PR |
| БМ-3 | encode_batch в UseCase | покрыт БМ-1 | ×10 encode | — |
| БМ-4 | to_thread overhead | покрыт БМ-1 | — | — |
| БМ-5 | CUDA streams | 4–8 ч | +10–20% | 🟡 если БМ-1 мало |
| БМ-6 | Bulk save | 2–3 ч | +5–8% | 🟡 в том же PR с БМ-1 |
| БМ-7 | Multi-label classifier | 3–5 дней | **10×** | 🔵 отдельный проект |
| БМ-8 | INT8 quantization | 2–3 дня | 1.5–2× | 🔵 |
| БМ-9 | Horizontal scaling | 1 день + БМ-1 | N× реплик | 🔵 |

### Рекомендованный план действий

**Итерация 1 — «быстрая победа» (1–2 дня работы)**: БМ-1 + БМ-2 + БМ-6 одним PR.
- Добавить `classify_batch` / `analyze_batch` / `extract_batch` на доменных интерфейсах
- Реализовать в torch-адаптерах через list-input `pipeline()`
- Переписать `ProcessContentUseCase.execute` на batched pipeline
- Добавить `save_features_bulk` в repository
- Тесты: unit (каждый новый метод) + integration (весь UseCase end-to-end, testcontainers PostgreSQL)
- Ожидаемый throughput: **25–40 постов/мин** (6–10× сейчас)

**Итерация 2 — если всё ещё мало (1 день)**: БМ-9.
- `SELECT FOR UPDATE SKIP LOCKED` в `get_unprocessed`
- Заменить APScheduler content_processing на локальный asyncio loop с env `REC_POLL_INTERVAL=5`
- Deploy через `docker compose --scale rec-worker=2` (VRAM позволяет)
- Ожидаемый throughput: **50–80 постов/мин** (2× от Итерации 1)

**Итерация 3 — дальняя оптимизация**: БМ-7 multi-label классификатор (неделя работы, но убирает 70% затрат).

---

## 4. Запуск работы (если решите)

Для реализации Итерации 1 через rec-system внутренний `/dev` pipeline:

```bash
cd /home/mattew/SKD/rec-system
claude -p --dangerously-skip-permissions --output-format json "$(cat <<'PROMPT'
/dev Performance: batched NLP pipeline in ProcessContentUseCase

Goal: accelerate rec-worker content processing from ~4 posts/min to 25+/min by
eliminating the per-post serial loop and leveraging native batch APIs of
HuggingFace pipeline + SentenceTransformer + spaCy.

Changes required (follow project TDD protocol — RED then GREEN per subtask,
strict Clean Architecture, all deps via DI container):

1. Domain interfaces (src/domain/interfaces/):
   - TopicClassifier: add abstract classify_batch(texts: list[str]) -> list[list[tuple[str, float]]]
   - SentimentAnalyzer: add abstract analyze_batch(texts: list[str]) -> list[tuple[str, float]]
   - EntityExtractor: add abstract extract_batch(texts: list[str]) -> list[dict[str, list[str]]]
   - ContentEncoder: already has encode_batch (leave as is)

2. Infrastructure adapters (src/infrastructure/nlp/):
   - torch_topic_classifier.py: implement classify_batch using self._pipe(list, ...)
     with batch_size=self._batch_size
   - torch_sentiment_analyzer.py: implement analyze_batch using self._pipe(list, ...)
     with batch_size=self._batch_size
   - spacy_entity_extractor.py: implement extract_batch using self._nlp.pipe(texts,
     batch_size=16) wrapped in asyncio.to_thread

3. Persistence layer (src/infrastructure/persistence/pg_content_repository.py):
   - Add save_features_bulk(features_list: list[ContentFeatures]) method that does:
     - one bulk INSERT … ON CONFLICT DO UPDATE on posts_features
     - one bulk UPDATE raw_content SET is_processed_by_rec=true WHERE id = ANY(:ids)
     - all inside a single transaction
   - Keep save_features() for backward compatibility (optional — can deprecate).

4. Application layer (src/application/use_cases/process_content.py):
   - Refactor execute() to batch mode:
     - Prepare all nlp_texts in a list comprehension
     - Call classify_batch, analyze_batch, encode_batch, extract_batch ONCE each
     - Build features_list in a zip loop
     - Call save_features_bulk(features_list)
   - Keep per-post timeout semantics: if the batch takes > batch_timeout seconds,
     log and save fallback features for any posts not yet processed

5. Tests (tests/unit + tests/integration):
   - Unit: each new batch method on each adapter (mock underlying pipeline/SentenceTransformer/spaCy)
   - Integration: full ProcessContentUseCase end-to-end via testcontainers PostgreSQL,
     with real (or lightweight-mocked) NLP adapters. Verify:
     - N posts go in → N features come out with correct topic/sentiment/entities/embedding
     - raw_content flag updated for exactly those posts
     - no stale posts_features rows for failed posts

Do NOT change:
- Model selection (keep cointegrated/rubert-* and blanchefort/rubert-*)
- batch_size env var name REC_CONTENT_BATCH_SIZE
- APScheduler cadence (5 min content_processing)
- Kafka consumer logic

Acceptance criteria:
- All unit + integration tests green
- With REC_CONTENT_BATCH_SIZE=20, a single tick processes all 20 posts in < 10 sec
  (measured via a log entry "batch_processed count=20 duration=Xs")
- No change to posts_features row schema

Commit convention per project TDD: one RED commit per subtask (failing test),
one GREEN commit per subtask (implementation). Refactor commits after GREEN if needed.
PROMPT
)"
```

Ориентировочное время автономного выполнения: **1.5–2.5 часа** (researcher → planner → 4 subtask'а RED/GREEN × implementer/test-writer/test-runner → reviewer).

После завершения — `docker compose build rec-worker`, `docker compose -f docker-compose.ml.yml up -d --force-recreate rec-worker`, и лог `batch_processed count=20 duration=X.Xs` в `docker logs ml-rec-worker` подтвердит эффект.
