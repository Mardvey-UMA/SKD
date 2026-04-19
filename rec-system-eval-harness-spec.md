# Technical Specification: Rec-System Offline Evaluation Harness

**Project**: `rec-system` (Python FastAPI recommendation engine)
**Version**: 1.0
**Created**: 2026-04-18
**Target project directory**: `/home/mattew/SKD/rec-system/`
**Estimated effort**: 12–18 рабочих дней (1 engineer, strict TDD per project CLAUDE.md)
**Execution mode**: delegated to sub-claude via `/dev` or sequential `/fix` calls

---

## 0. Context (must-read before starting)

### What exists

- `rec-system` — FastAPI сервис c Clean Architecture (domain / application / infrastructure / presentation). See `rec-system/CLAUDE.md` for conventions — strict TDD (RED → GREEN), `uv run` for all python, ~670 unit + integration tests currently.
- Shared Postgres `content_agg_db` with `data_flow` schema. Rec-system owns tables: `posts_features`, `rec_profiles`, `rec_entity_interests`, `rec_config`, `recommendation_history`, `user_interactions`, `categories`.
- Currently deployed: `ml-rec-worker` container, processing ~100 posts/min via APScheduler into `posts_features`.
- Pre-computed NLP features exist for ~8500 posts: topics (18 categories from `categories` table), sentiment, NER entities, 312-dim embeddings (rubert-tiny2).
- Playground notebook exists at `rec-system/rec_playground.ipynb` (to be extended, not replaced).
- Extended corpus is AVAILABLE at `/home/mattew/SKD/telegram_posts.csv` (114 MB, 153 919 rows, schema: `message_id,channel_username,channel_title,content,post_date,channel_url,views`). Phase 0 MUST ingest a stratified subset of ~30k rows. See Phase 0 for full column mapping and sampling strategy.

### What does NOT exist yet

- Any evaluation / benchmarking framework
- Persona / synthetic user simulation
- LLM-based quality judging
- Proxy metrics computation (topic entropy, diversity, freshness, etc.)
- Explainability endpoint for scoring breakdown
- Comparative benchmarking runner
- Extended playground with side-by-side comparison

### Why this project

Rec-system has no live users. We cannot measure CTR / time-on-feed / scroll-depth. Without offline evaluation, every change to scoring, retrieval, or models is a blind modification — we won't know if it helps or hurts. This harness gives us **reproducible, automated quality signals** before first user interactions.

---

## 1. Goals

1. Build a persona-driven benchmark that measures recommendation quality without real users.
2. Automate quality scoring via LLM-as-judge (Claude Haiku) and proxy metrics.
3. Support side-by-side comparison of different system configurations (before/after changes).
4. Produce rich, reproducible artifacts for every benchmark run.
5. Provide an interactive notebook for hands-on qualitative assessment.
6. Enable debugging via explainability — "why was post X ranked at position Y for user Z".

## 2. Non-goals (explicit)

- ❌ NOT adding cross-encoder reranking or live profile recomputation in this scope. This spec ONLY builds the harness; those features are evaluated LATER using it.
- ❌ NOT modifying existing scoring formula, retrieval logic, or NLP pipeline.
- ❌ NOT touching parser, dedup-system, backend microservices, or frontend.
- ❌ NOT building a new production API. Explainability endpoint is dev-only (feature-flagged).
- ❌ NOT implementing real user tracking or A/B testing infrastructure.
- ❌ NOT reimplementing any existing rec-system logic — wrap and reuse.
- ❌ NOT training new models. Use Claude CLI for judging; all other NLP stays as-is.

## 3. Success Criteria (acceptance)

Agent MUST deliver:

- [ ] All 8 phases completed (Phase 0 conditionally), each with RED + GREEN commits per subtask.
- [ ] Minimum 20 persona definitions in YAML, covering diverse interest profiles.
- [ ] Deterministic behavior simulator that produces a synthetic user (profile + interactions) from any persona.
- [ ] `/recommendations` callable against synthetic users with ordered feed output.
- [ ] 8 proxy metrics computed on any feed result (topic entropy, freshness median, pairwise cosine avg, topic coverage, distance to profile, long-tail ratio, dedup pair count, category mass balance).
- [ ] LLM-as-judge flow working via `claude -p --model haiku` — input persona + feed, output per-post relevance 0–5.
- [ ] CLI `scripts/benchmark.py` that runs full pipeline for N personas × M versions × K runs, producing Markdown report + CSVs + per-feed JSONs in `.claude/artifacts/eval/`.
- [ ] Playground notebook with side-by-side feed comparison, persona selector, version selector, explainability breakdown.
- [ ] Explainability endpoint (`POST /recommendations/explain`) returning per-component scoring breakdown.
- [ ] All code covered by unit + integration tests, `uv run pytest` passes with zero new failures.
- [ ] Markdown summary at `.claude/artifacts/eval/README.md` documenting how to run everything.

Functional acceptance test (must pass as E2E):
```bash
cd /home/mattew/SKD/rec-system
uv run python scripts/benchmark.py \
  --personas tech-geek,political-junkie,drifting-user \
  --versions baseline \
  --runs 2 \
  --output .claude/artifacts/eval/smoke_test/
```
Exit code 0. Produces `summary.md`, `metrics.csv`, per-run JSONs. Takes < 10 minutes.

## 4. Architecture Overview

### Where new code lives (follows rec-system Clean Architecture)

```
rec-system/
├── tests/evaluation/                    # NEW — eval framework
│   ├── __init__.py
│   ├── personas/                        # YAML persona definitions
│   │   ├── tech_geek.yaml
│   │   ├── political_junkie.yaml
│   │   └── ... (20+ files)
│   ├── persona_loader.py                # Load YAML → Persona dataclass
│   ├── behavior_simulator.py            # Persona → synthetic interactions + profile
│   ├── feed_evaluator.py                # Run GenerateFeedUseCase, collect FeedResult
│   ├── proxy_metrics.py                 # Entropy, freshness, etc.
│   ├── llm_judge.py                     # Claude CLI wrapper for scoring
│   ├── benchmark_runner.py              # Orchestrate multi-persona × multi-version runs
│   ├── report_writer.py                 # Generate Markdown + CSV artifacts
│   ├── conftest.py                      # pytest fixtures
│   └── tests/                           # Unit tests of the eval framework
│       ├── test_persona_loader.py
│       ├── test_behavior_simulator.py
│       ├── test_proxy_metrics.py
│       └── ...
├── src/presentation/api/routes/explain.py   # NEW — dev explainability endpoint
├── src/application/services/scoring_explainer.py  # NEW — extract scoring internals
├── scripts/                             # NEW — CLI entry points
│   ├── benchmark.py                     # Main runner
│   ├── ingest_telegram_csv.py           # Phase 0 — ingest CSV to raw_content
│   └── seed_synthetic_corpus.py         # Fallback: generate synthetic posts if no CSV
├── notebooks/                           # NEW (or extend existing rec_playground.ipynb)
│   └── rec_playground.ipynb             # Side-by-side comparison tool
└── .claude/artifacts/eval/              # NEW — all benchmark outputs
    ├── README.md                        # How to run, how to read results
    ├── personas_registry.md             # Catalog of all personas, purpose, interests
    ├── benchmarks/
    │   └── {timestamp}_{tag}/           # One folder per run
    │       ├── summary.md
    │       ├── metrics.csv
    │       ├── personas_snapshot.yaml
    │       ├── config_snapshot.yaml
    │       ├── feeds/
    │       │   └── {persona}_{version}_run{N}.json
    │       └── llm_judgments/
    │           └── {persona}_{version}_run{N}.json
    └── comparisons/
        └── {ts}_{versionA}_vs_{versionB}/
            ├── summary.md
            └── diff.csv
```

### Data flow

```
┌─────────────┐      ┌───────────────────────┐      ┌──────────────────┐
│  Persona    │─────▶│ BehaviorSimulator     │─────▶│ Synthetic user:  │
│  (YAML)     │      │ - onboarding          │      │ - rec_profiles   │
└─────────────┘      │ - N interactions      │      │ - interactions   │
                     │ - apply via UseCase   │      │ (test DB)        │
                     └───────────────────────┘      └────────┬─────────┘
                                                             │
                                                             ▼
                     ┌───────────────────────┐      ┌──────────────────┐
                     │ GenerateFeedUseCase   │◀─────│ FeedEvaluator    │
                     │ (unchanged)           │      │ - invoke         │
                     └───────────┬───────────┘      │ - collect items  │
                                 │                   │ - load features  │
                                 ▼                   └────────┬─────────┘
                     ┌───────────────────────┐                │
                     │ Ordered feed (30 IDs) │                │
                     └───────────────────────┘                │
                                                              ▼
                                                ┌─────────────────────────┐
                                                │ Three evaluation paths: │
                                                │ ┌─────────────────────┐ │
                                                │ │ ProxyMetrics        │ │
                                                │ │ → entropy, fresh... │ │
                                                │ └─────────────────────┘ │
                                                │ ┌─────────────────────┐ │
                                                │ │ LLMJudge            │ │
                                                │ │ → claude -p haiku   │ │
                                                │ │ → [0-5]×30 scores   │ │
                                                │ └─────────────────────┘ │
                                                │ ┌─────────────────────┐ │
                                                │ │ Explain per-post    │ │
                                                │ │ → component break   │ │
                                                │ └─────────────────────┘ │
                                                └─────────────────────────┘
                                                              │
                                                              ▼
                                                ┌─────────────────────────┐
                                                │ ReportWriter            │
                                                │ → Markdown + CSV + JSON │
                                                │ → .claude/artifacts/    │
                                                └─────────────────────────┘
```

### Key architectural principles

- **Read-only against production data**: evaluation runs against a COPY of the DB (testcontainers PostgreSQL OR a dedicated eval schema). Never write synthetic users into `data_flow.rec_profiles` on live DB.
- **Versions are config-level, not code-level where possible**: `baseline`, `live_profile`, `rerank` are configurations of the same `GenerateFeedUseCase`, toggled via environment vars or rec_config.
- **Determinism**: behavior simulator uses a seeded RNG (per-run seed). Same seed = same interactions = same feed = reproducible results.
- **Rich artifacts**: every benchmark run saves enough data to fully re-analyze later without re-running. Raw feed dumps, LLM judgments, persona snapshots, config snapshots.

---

## 5. Orchestration Model (MUST READ — directions for SKD-level orchestrator)

This spec is designed for **two-level delegation**:

1. **SKD orchestrator** (session at `/home/mattew/SKD/`) — persists across phases. Reads this spec, tracks progress, delegates each phase as a separate fresh `claude -p` invocation in rec-system.
2. **rec-system sub-claude** (fresh session per phase, launched from `/home/mattew/SKD/rec-system/`) — executes ONE phase, commits, reports back.

Orchestrator NEVER writes code. Sub-claude never sees other phases — only current one.

### 5.1. Progress tracker (mandatory artifact)

Orchestrator maintains a living state document at `/home/mattew/SKD/.claude/artifacts/rec-eval/orchestration_state.md`:

```markdown
# Rec-Eval Harness Orchestration State

**Started**: 2026-04-18 12:00 UTC
**Spec**: /home/mattew/SKD/rec-system-eval-harness-spec.md
**Target project**: /home/mattew/SKD/rec-system/
**Base branch**: feat/eval-harness (commit hash at start: XXXX)

## Progress

| Phase | Status | Started | Finished | Commits | Notes |
|-------|--------|---------|----------|---------|-------|
| 0 | ✅ done | 12:05 | 13:40 | abc1234, def5678, ghi9012 | CSV ingested 28 500 rows, drain 75 min |
| 1 | 🔄 in_progress | 13:45 | — | — | Persona loader RED commit pending |
| 2 | ⏸ queued | — | — | — | Depends on 1 |
| 3 | ⏸ queued | — | — | — | Depends on 2 |
| 4 | ⏸ queued | — | — | — | Can start after 3 |
| 5 | ⏸ queued | — | — | — | Can start after 3 |
| 6 | ⏸ queued | — | — | — | Depends on 3,4,5 |
| 7 | ⏸ queued | — | — | — | Depends on 6,8 |
| 8 | ⏸ queued | — | — | — | Can run in parallel with 4,5,6 |

## Delegation history

Per phase: session_id of sub-claude, duration, cost, result summary, retry count.

## Pending issues / blockers

Any issues the orchestrator hit that need user attention.

## Next action

Exact prompt orchestrator plans to send next, for user review.
```

Orchestrator updates this file after every delegation — before and after.

### 5.2. Phase dependency graph

```
    Phase 0 (ingestion)
         │
         ▼
    Phase 1 (personas)
         │
         ▼
    Phase 2 (simulator)
         │
         ▼
    Phase 3 (feed evaluator)
         │
    ┌────┼────┬─────┐
    ▼    ▼    ▼     ▼
   P4   P5   P8   (nothing)
  (proxy) (LLM) (explain)
    │    │    │
    └────┴────┘
         │
         ▼
    Phase 6 (benchmark runner)
         │
         ▼
    Phase 7 (playground notebook)
```

Phases 4, 5, 8 are **independent** of each other — orchestrator CAN run them in parallel (two concurrent `claude -p` calls). But keep it simple: sequential is fine, parallel gains ~30 min out of a day of work.

### 5.3. Per-phase delegation template

Orchestrator builds ONE fresh invocation per phase. Sub-claude sees:

1. **Current progress summary** — what's done before this phase (prevents re-implementing existing code).
2. **Focused task** — only this phase's content from the spec (agent doesn't need to read all 8 phases to do Phase 2).
3. **References** — path to full spec, progress tracker, any prior artifacts.
4. **Explicit TDD instructions** — RED commit → GREEN commit, `uv run pytest` gate before final commit.

**Prompt template** (orchestrator fills in placeholders):

```
You are executing Phase {N} of the rec-system evaluation harness project.

═══════════════════════════════════════════════════════════════
PROGRESS SO FAR
═══════════════════════════════════════════════════════════════
{progress_summary — copied from orchestration_state.md}

Previous commits on branch feat/eval-harness:
{last 10 commits from git log --oneline}

Key artifacts already produced:
- /home/mattew/SKD/.claude/artifacts/rec-eval/corpus_snapshot.md  (if Phase 0 done)
- /home/mattew/SKD/rec-system/tests/evaluation/personas/*.yaml    (if Phase 1 done)
- ... (etc)

═══════════════════════════════════════════════════════════════
YOUR TASK — PHASE {N}: {phase_name}
═══════════════════════════════════════════════════════════════

{full phase section copy-pasted from spec — ONLY this phase}

═══════════════════════════════════════════════════════════════
EXECUTION RULES
═══════════════════════════════════════════════════════════════

- Working directory: /home/mattew/SKD/rec-system/
- Branch: feat/eval-harness (already created by orchestrator)
- Follow project TDD protocol per CLAUDE.md: RED commit → GREEN commit → REFACTOR if needed.
- Use `uv run pytest` for ALL test runs — never bare pytest.
- Before final commit, full test suite must be green: `uv run pytest -m "unit or integration" --tb=short`.
- Commit messages follow conventional commits per project CLAUDE.md.
- Do NOT re-implement anything from earlier phases listed as "already produced".
- Do NOT touch code outside the scope of this phase.
- If you discover a bug in earlier phase code that blocks you: STOP, add to pending_issues, return a BLOCKED status.

═══════════════════════════════════════════════════════════════
REFERENCES
═══════════════════════════════════════════════════════════════

- Full spec (for context, DO NOT re-execute other phases): /home/mattew/SKD/rec-system-eval-harness-spec.md
- Orchestration state: /home/mattew/SKD/.claude/artifacts/rec-eval/orchestration_state.md
- Project CLAUDE.md (loaded automatically): /home/mattew/SKD/rec-system/CLAUDE.md
- Design docs: /home/mattew/SKD/rec-system/design/

═══════════════════════════════════════════════════════════════
RETURN FORMAT
═══════════════════════════════════════════════════════════════

After finishing, your final message must include:

### PHASE {N} RESULT

STATUS: DONE | BLOCKED | FAILED
Commits: <list of commit hashes>
New files: <list of paths>
Modified files: <list of paths>
Tests added: <count> unit, <count> integration
Tests run summary: <N passed, M failed, K skipped>
Artifacts produced: <paths under .claude/artifacts/rec-eval/ or tests/evaluation/>
Duration: <minutes>
Issues encountered: <brief list or "none">
```

### 5.4. Invocation command (orchestrator uses this for each phase)

```bash
cd /home/mattew/SKD/rec-system && claude -p "$(cat <<'PROMPT'
{filled-in template from 5.3}
PROMPT
)" \
    --dangerously-skip-permissions \
    --output-format json \
    --max-turns 80 \
    --model sonnet \
    > /tmp/rec-eval-phase-{N}.json 2>&1
```

`--max-turns 80` tuned per phase: Phase 0 and Phase 1 may need more (ingestion + 20 YAMLs), Phases 3/4/8 less. Orchestrator should use:

| Phase | `--max-turns` |
|-------|--------------|
| 0 | 120 (includes NLP drain polling) |
| 1 | 100 (20 YAMLs + loader) |
| 2 | 80 |
| 3 | 40 |
| 4 | 60 |
| 5 | 60 |
| 6 | 100 |
| 7 | 60 |
| 8 | 50 |

### 5.5. Verification between phases (orchestrator responsibilities)

After each sub-claude returns, orchestrator MUST:

1. **Parse JSON**: check `is_error=false`, extract `result` and `session_id`.
2. **Verify commits**: `cd rec-system && git log --oneline -10` — confirm commit hashes exist, match what sub-claude reported.
3. **Verify tests** (quick smoke): `cd rec-system && uv run pytest tests/evaluation -m unit -q --tb=no | tail -3`.
4. **Verify artifacts**: check expected files exist at expected paths.
5. **Update progress tracker**: mark phase DONE, record commit hashes, session_id, cost, duration.
6. **Commit tracker state**: `cd /home/mattew/SKD && git add .claude/artifacts/rec-eval/orchestration_state.md && git commit -m "chore(eval): update orchestration state after Phase N"`.
7. **Show user a summary** before proceeding: what done, cost so far, next phase plan.

If user has approved autonomous execution (per autonomous_to_done memory), proceed to next phase without waiting. Otherwise, wait for explicit go-ahead.

### 5.6. Failure handling

Sub-claude may return status FAILED or BLOCKED. Orchestrator behavior:

| Outcome | Orchestrator action |
|---------|--------------------|
| STATUS: DONE, tests green | Record, proceed to next phase |
| STATUS: DONE, tests failing | Re-delegate with explicit "fix test failures" task, max 2 retries |
| STATUS: BLOCKED | STOP. Write pending_issues to tracker. Ask user. |
| STATUS: FAILED, retryable | Re-invoke with same prompt + "RETRY {N}/3: previous attempt failed: {truncated_error}". Max 3 retries. |
| STATUS: FAILED, not retryable | STOP. Report to user. |
| JSON parse error | Re-invoke once. If still bad, STOP. |
| Timeout (exceeds max-turns) | Halve scope, retry. If still times out: split phase into sub-tasks manually, re-delegate. |

### 5.7. Cost budget

Hard limits (orchestrator aborts if exceeded):
- Single phase: **$15** max
- Total project: **$100** max
- If approaching limit, orchestrator asks user before continuing.

### 5.8. Final deliverable

After all 8 phases complete, orchestrator produces:
1. Final summary at `.claude/artifacts/rec-eval/final_report.md` — what shipped, what didn't, total commits, total cost, any open issues.
2. Runs the smoke benchmark once to validate end-to-end (`scripts/benchmark.py ... --tag final_smoke`).
3. Reports to user.

### 5.9. What orchestrator should NOT do

- ❌ NEVER write implementation code directly — only prompt construction and state tracking.
- ❌ NEVER skip the commit verification step.
- ❌ NEVER re-run a phase without checking if it's already DONE in the tracker.
- ❌ NEVER merge `feat/eval-harness` branch automatically. User merges after reviewing final_report.md.
- ❌ NEVER delete or rewrite the progress tracker. Append-only.

---

## 6. Phases (ordered execution)

Each phase = independent TDD unit (RED commit → GREEN commit → REFACTOR commit). Phase boundaries are commit boundaries. Agent MUST verify tests green at end of each phase before proceeding.

### Phase 0: Content Ingestion (REQUIRED — 1–2 days)

**Goal**: Ingest a subset of `telegram_posts.csv` into `data_flow.raw_content` so the benchmark corpus is large and diverse enough to produce meaningful results.

**Known inputs** (verified):

- **CSV path**: `/home/mattew/SKD/telegram_posts.csv`
- **Size**: 119 829 214 bytes (≈114 MB)
- **Rows**: 153 920 total (153 919 data rows + 1 header)
- **Header**: `message_id, channel_username, channel_title, content, post_date, channel_url, views`
- **Sample row**:
  ```
  "33679212544","mosnews","Московская Хроника","😱 Кофе со вкусом Kinder Bueno можно попробовать в Москве. Стоит такое удовольствие 290 рублей.","2025-11-13 01:30:58","https://t.me/mosnews","20245"
  ```
- **Content language**: Russian.
- **Post date range**: needs measurement — may contain recent and very old posts. Agent should compute min/max `post_date` before ingestion.

**Column mapping** (CSV → `data_flow.raw_content`):

| CSV column | Target | Notes |
|---|---|---|
| `message_id` | Part of `external_id` | Use composite: `f"telegram:{channel_username}:{message_id}"` to ensure uniqueness across channels |
| `channel_username` | Into `raw_data` JSONB as `channel` | Also include in composite `external_id` |
| `channel_title` | Into `raw_data` JSONB as `title` (NOTE: this is channel name, not post title — telegram posts rarely have title field. If `content` has a line with emojis/headline followed by body, agent MAY split first line as title. Simpler: leave title null) |
| `content` | `clean_text` (also `raw_data.content`) | If empty → skip row |
| `post_date` | `received_at`, also into `raw_data.publishedAt` (ISO format) | Parse as timestamp UTC |
| `channel_url` | Into `raw_data` JSONB | — |
| `views` | Into `raw_data.views` (int) | Optional |

**Additional fields to set**:

- `processing_status = 'COMPLETED'` (parser done, NLP pending)
- `source_type = 'telegram'`
- `source_id` — create ONE row in `config.sources` first with `name='eval_telegram_import'`, take its UUID, pass as `--source-id` argument
- `is_processed_by_rec = FALSE` (so content_processing job picks up and builds features)
- `is_processed_by_dedup = FALSE` (so dedup-worker picks up)
- `cleaning_status = 'COMPLETED'`, `clean_text_length = length(content)`
- `received_at = parsed_post_date`, `cleaned_at = parsed_post_date`
- `created_at = now()`, `updated_at = now()`

**Steps**:

1. **Check file exists** at the path above. If not, STOP and ask user.

2. **Compute stats** (read-only, don't load full file into memory):
   ```python
   # In script — stream through with csv.DictReader
   total_rows = 0
   empty_content = 0
   min_date, max_date = None, None
   channels = set()
   for row in reader:
       total_rows += 1
       if not row['content'].strip(): empty_content += 1
       d = parse(row['post_date'])
       min_date = min(min_date or d, d); max_date = max(max_date or d, d)
       channels.add(row['channel_username'])
   ```
   Save stats to `.claude/artifacts/eval/csv_stats.md` for later reference.

3. **Create source row** in `config.sources` table:
   ```sql
   INSERT INTO config.sources (id, name, url, source_type, is_active, created_at)
   VALUES (gen_random_uuid(), 'eval_telegram_import', 'csv://telegram_posts.csv', 'telegram', false, NOW())
   RETURNING id;
   ```
   Capture the UUID.

4. **Write** `scripts/ingest_telegram_csv.py` with arguments:
   - `--csv-path` (default: `/home/mattew/SKD/telegram_posts.csv`)
   - `--limit N` (default: 30000 — see "Sampling strategy" below)
   - `--source-id UUID` (from step 3)
   - `--batch-size 1000` (inserts per transaction)
   - `--skip-empty` (skip rows where content is empty)
   - `--min-length 50` (skip very short posts; Telegram has lots of "up 📉", "down 📈" one-liners that are noise)
   - `--dry-run` (print what would insert, don't commit)
   - `--sample-strategy {head|random|recent|stratified_by_channel}` (see below)

5. **Sampling strategy** — default `stratified_by_channel`:
   - Count posts per channel in the CSV
   - Sample proportionally, capping at `max-per-channel = 500` (prevents 1–2 dominant channels from skewing corpus)
   - Ensures corpus represents multiple sources, not a monolith
   - Alternative: `recent` — takes top N by `post_date` descending

6. **Run**:
   ```bash
   cd /home/mattew/SKD/rec-system
   uv run python scripts/ingest_telegram_csv.py \
       --csv-path /home/mattew/SKD/telegram_posts.csv \
       --limit 30000 \
       --source-id <UUID_from_step_3> \
       --sample-strategy stratified_by_channel \
       --min-length 50
   ```
   Expected time: 1–3 minutes for 30k insert.

7. **Verify ingestion**:
   ```sql
   SELECT COUNT(*), COUNT(DISTINCT raw_data->>'channel') AS channels,
          MIN(received_at), MAX(received_at)
   FROM data_flow.raw_content
   WHERE source_type='telegram';
   ```
   Expected: ~30 000 rows, 10+ distinct channels, spans expected date range.

8. **Drain NLP backlog** (temporary tuning for faster eval data prep):
   ```bash
   # In docker-compose.ml.yml, temporarily:
   REC_CONTENT_BATCH_SIZE: "200"    # up from 50
   REC_CONTENT_JOB_INTERVAL_SECONDS: "20"   # down from 30
   # Redeploy rec-worker. Same for dedup-worker equivalent env.
   ```
   Monitor progress:
   ```sql
   SELECT COUNT(*) FILTER (WHERE is_processed_by_rec=false AND source_type='telegram') AS pending_rec,
          COUNT(*) FILTER (WHERE is_processed_by_dedup=false AND source_type='telegram') AS pending_dedup
   FROM data_flow.raw_content;
   ```
   At 400/min throughput, 30k posts drain in ~75 min. Agent must wait (or poll every 5 min) until both queues reach zero.

9. **Revert env overrides** back to 50/30 after drain complete.

10. **Document corpus stats** in `.claude/artifacts/eval/corpus_snapshot.md`:
    - Total posts in DB (original + ingested)
    - Channels represented
    - Date range
    - Topic distribution (if NLP complete): SELECT topic_1, COUNT(*) FROM posts_features GROUP BY 1 ORDER BY 2 DESC
    - This is the "ground state" for benchmarks — so future runs can be compared fairly

**Sampling rationale — why 30k, not all 153k**:

- Full NLP processing rate: ~100 posts/min current, ~400/min with temporary tuning
- 150k posts × 150 ms NLP = 6h+ even with tuning
- Diminishing returns on corpus diversity past ~20k (Shannon entropy of topics plateaus)
- 30k is enough to cover all 18 topic categories with 500+ posts each on average
- Agent may override via `--limit` if time budget allows

**Edge cases to handle in script**:

- CSV row with malformed date → log warning, skip
- Content column contains CSV quotes that weren't escaped → use `csv.QUOTE_ALL` and `escapechar='\\'` reader config
- Duplicate `(channel_username, message_id)` pairs → skip (CSV may have dupes)
- Content is just an emoji or URL → skip via `--min-length 50`
- Very long content (> 100k chars) → truncate to 100k, log warning
- `views` column may be empty or non-numeric → default to 0

**Tests**:
- Unit: row mapping with synthetic CSV fixture covering all edge cases (malformed date, duplicate, empty, too-short, normal).
- Integration (testcontainers PG): ingest 100-row fixture CSV, verify 100 rows in raw_content minus skipped, verify source row created.

**Commits** (TDD):
- `test(scripts): RED tests for telegram CSV ingestion (fixture + edge cases)`
- `feat(scripts): implement telegram_posts.csv ingestion with stratified sampling`
- `chore(infra): temporarily tune content_processing cadence for bulk NLP backlog drain`
- `chore(infra): revert content_processing cadence after drain complete`

**Acceptance**:
- `SELECT COUNT(*) FROM data_flow.raw_content WHERE source_type='telegram'` ≥ 25 000 (after dedup during ingestion).
- `SELECT COUNT(*) FROM data_flow.posts_features pf JOIN data_flow.raw_content rc ON pf.post_id=rc.id WHERE rc.source_type='telegram'` ≥ 25 000 (NLP drained).
- `.claude/artifacts/eval/corpus_snapshot.md` exists and documents final state.

---

### Phase 1: Persona Framework (2 days)

**Goal**: Data model + 20 hand-crafted personas + loader + catalog.

**Deliverables**:

1. **Dataclass** `tests/evaluation/models.py`:
```python
from dataclasses import dataclass
from typing import Literal
from uuid import UUID

@dataclass(frozen=True)
class InteractionPattern:
    like_probability_on_interest: float          # 0.0–1.0
    bookmark_probability_on_interest: float      # on strong interest
    dislike_probability_on_dislike: float        # on disliked category
    skip_probability_on_dislike: float           # short IMPRESSION close
    avg_dwell_seconds: float                     # mean dwell on interesting content
    scroll_depth_p50: float                      # 0.0–1.0
    interactions_per_session: int                # how many events we simulate

@dataclass(frozen=True)
class Persona:
    id: str                                      # slug: "tech-geek"
    display_name: str                            # "Tech Enthusiast"
    description: str                             # natural-language, used for LLM judge
    base_interests: dict[str, float]             # {category: weight}, sums to 1.0
    dislikes: dict[str, float]                   # {category: weight}, 0.0–1.0
    preferred_entities: list[str]                # e.g., ["OpenAI", "Илон Маск"]
    pattern: InteractionPattern
    drift_phase2: dict[str, float] | None = None # optional: after N interactions, shift interests
    drift_after_interactions: int = 0
    language: Literal["ru"] = "ru"
```

2. **20 persona YAML files** in `tests/evaluation/personas/`. Required diversity — MUST cover all of:

| ID | Description (short) |
|---|---|
| `tech-geek` | Technology + science, avoids politics |
| `political-junkie` | Politics + international news, narrow focus |
| `business-follower` | Business + economics + finance |
| `sports-fan` | Sports exclusively |
| `balanced-reader` | Uniform distribution across all 18 topics |
| `entertainment-only` | Культура + развлечения only |
| `health-focused` | Здоровье + наука (medical angle) |
| `breaking-news-chaser` | Any topic, but recency matters most (pattern tweaks) |
| `long-form-reader` | Prefers `is_long_form=true` posts |
| `skimmer` | Prefers `is_short_form=true` |
| `negative-sentiment-seeker` | Reads mostly negative sentiment content (true behavior of some news consumers) |
| `entity-focused-musk` | Interest triggered by "Илон Маск" mentions |
| `entity-focused-putin` | Interest triggered by "Путин" mentions |
| `drifting-tech-to-politics` | Phase 1: tech; phase 2 (after 10 interactions): politics |
| `cold-start-empty` | No interactions, just onboarding categories (testing cold-start quality) |
| `science-only` | Narrow: наука only |
| `crime-reader` | Происшествия + криминал |
| `military-watcher` | Армия + международные новости |
| `transport-enthusiast` | Транспорт + технологии (niche) |
| `education-culture` | Образование + культура |

Example YAML:
```yaml
# tests/evaluation/personas/tech_geek.yaml
id: tech-geek
display_name: Technology Enthusiast
description: |
  Deeply interested in technology, AI, science breakthroughs, and tech business.
  Reads long articles about AI models, engineering, space exploration.
  Actively avoids political content and sports news.
  Tends to bookmark for later reading, likes articles > 1000 words.
base_interests:
  технологии: 0.45
  наука: 0.30
  бизнес: 0.15
  образование: 0.05
  здоровье: 0.05
dislikes:
  политика: 0.90
  спорт: 0.80
  развлечения: 0.50
preferred_entities:
  - OpenAI
  - Google
  - Tesla
  - SpaceX
  - Илон Маск
pattern:
  like_probability_on_interest: 0.75
  bookmark_probability_on_interest: 0.25
  dislike_probability_on_dislike: 0.60
  skip_probability_on_dislike: 0.85
  avg_dwell_seconds: 35
  scroll_depth_p50: 0.80
  interactions_per_session: 25
```

3. **Loader** `tests/evaluation/persona_loader.py`:
```python
class PersonaLoader:
    def __init__(self, personas_dir: Path):
        self._dir = personas_dir

    def load(self, persona_id: str) -> Persona: ...
    def load_all(self) -> list[Persona]: ...
    def validate(self, persona: Persona) -> None:
        # base_interests sum ≈ 1.0, all keys in valid TOPICS, etc.
        ...
```

4. **Catalog** `.claude/artifacts/eval/personas_registry.md`:
   - Table of all personas with id, display_name, key characteristic
   - For human review — ensure diversity

**Tests**:
- `test_persona_loader.py`: loads valid YAML, rejects malformed, validates invariants (interests sum), handles unknown topic names.
- `test_persona_coverage.py`: assert all 18 topics appear in at least one persona's interests.

**Commits**:
- `test(evaluation): RED tests for persona loader and validation`
- `feat(evaluation): implement persona loader and dataclass models`
- `feat(evaluation): add 20 persona definitions`

---

### Phase 2: Behavior Simulator (2 days)

**Goal**: Turn a Persona into a synthetic user state in the DB — `rec_profiles` row + `user_interactions` rows — by simulating deterministic interactions.

**Contract**:

```python
class BehaviorSimulator:
    def __init__(
        self,
        content_repo: ContentRepository,
        profile_repo: UserProfileRepository,
        interactions_repo: InteractionsRepository,
        signal_classifier: SignalClassifier,
        update_profile_use_case: UpdateProfileUseCase,
        onboard_use_case: OnboardUserUseCase,
    ):
        ...

    async def simulate(
        self,
        persona: Persona,
        user_id: UUID,
        seed: int = 42,
    ) -> SimulatedUserState:
        """
        1. Sample onboarding categories from persona.base_interests (top-3 by weight).
        2. Call OnboardUserUseCase(user_id, categories) — creates empty profile.
        3. For each interaction in persona.pattern.interactions_per_session:
           a. Pick a "seen" post sampled from corpus matching persona tastes
              (weighted by match: high if post.topic ∈ base_interests).
           b. Decide event type based on persona.pattern + topic match:
              - Interest match → LIKE (75%) / BOOKMARK (25%) / long IMPRESSION (remaining)
              - Dislike match → DISLIKE (60%) / short IMPRESSION skip
              - Neutral → short IMPRESSION
           c. Generate UserInteraction row: event_id=uuid4(seed), duration_ms, scroll_depth from persona.pattern.
           d. Save to user_interactions (processed=false).
           e. Handle drift: if interaction count >= persona.drift_after_interactions, switch to drift_phase2 distribution.
        4. Run UpdateProfileUseCase to batch-classify and apply EMA.
        5. Return SimulatedUserState with profile, interactions, sampled_posts.
        """
```

**Key design decisions**:

- **Deterministic**: `random.Random(seed)` throughout. Same persona + seed = same simulation.
- **Write to separate schema/DB**: benchmark should target `data_flow_eval` schema OR testcontainers DB, NEVER `data_flow` production. Implementation: add env var `REC_EVAL_SCHEMA=data_flow_eval` that all repositories respect for benchmark runs. OR use Alembic offline migration to create mirror schema. EASIER PATH: use testcontainers — spin fresh PG per benchmark.
- **Post sampling strategy**: given persona.base_interests, we want to sample posts from corpus where topic matches. Use weighted random: `weight[post] = sum(base_interests[t] * post.topic_score[t] for t in post.topics)`. This gives "realistic" browsing — user mostly sees posts aligned with interests, not random.
- **Edge cases**:
  - Empty corpus: fail gracefully, clear error.
  - Persona with zero interactions (cold-start): skip step 3, go straight to feed gen.
  - Drift past end of session: use drift_phase2 for events beyond threshold.

**Data model — `SimulatedUserState`**:
```python
@dataclass
class SimulatedUserState:
    user_id: UUID
    persona_id: str
    seed: int
    created_at: datetime
    onboarding_categories: list[str]
    interactions: list[UserInteraction]     # what was generated
    profile_snapshot: UserProfile           # AFTER UpdateProfileUseCase
    sampled_post_ids: list[UUID]            # what "they saw"
```

**Tests** (integration, testcontainers PostgreSQL):
- `test_simulate_tech_geek`: 25 interactions → profile.topic_vector has "технологии" as top
- `test_determinism`: same persona + seed → identical state (bit-exact comparison)
- `test_drift_persona`: after 10 interactions, top topic shifts from tech to politics
- `test_cold_start_persona`: zero interactions → profile.embedding is zero vector, top-3 categories = onboarding
- `test_dislike_avoidance`: posts in dislike categories almost never sampled

**Commits**:
- `test(evaluation): RED integration tests for BehaviorSimulator (5 personas)`
- `feat(evaluation): implement BehaviorSimulator with deterministic seed-based sampling`

---

### Phase 3: Feed Evaluator (1 day)

**Goal**: Invoke `GenerateFeedUseCase` for a synthetic user, collect structured feed result with all metadata needed for analysis.

**Contract**:

```python
@dataclass
class FeedItem:
    position: int                       # 1-30
    post_id: UUID                       # published_content.id
    raw_content_id: UUID                # upstream raw_content.id
    title: str
    content_snippet: str                # first 300 chars of clean_text
    topics: list[tuple[str, float]]     # [(topic, score)]
    sentiment: tuple[str, float]
    entities: dict[str, list[str]]
    embedding: list[float]
    text_metrics: dict
    source_id: UUID
    age_hours: float                    # at time of feed gen
    scoring_breakdown: dict | None      # populated only if explain=True

@dataclass
class FeedResult:
    user_id: UUID
    persona_id: str
    version: str                        # "baseline", "live_profile", etc.
    seed: int
    run_number: int
    generated_at: datetime
    items: list[FeedItem]
    request_latency_ms: float
    count: int

class FeedEvaluator:
    def __init__(
        self,
        generate_feed_use_case: GenerateFeedUseCase,
        content_repo: ContentRepository,
        published_repo: PublishedContentRepository,
    ):
        ...

    async def run(
        self,
        synthetic_user: SimulatedUserState,
        count: int = 30,
        explain: bool = False,
    ) -> FeedResult:
        ...
```

**Implementation notes**:

- Wrap existing `GenerateFeedUseCase` — do NOT duplicate retrieval logic.
- For `explain=True`, call the Phase 8 explainability service to populate `scoring_breakdown` per item. Only used by Playground Notebook.
- Serialize `FeedResult` to JSON for artifacts: `feeds/{persona}_{version}_run{N}.json`.

**Tests**:
- Unit: FeedResult serialization (to/from JSON round-trip).
- Integration (testcontainers): full pipeline persona → simulator → evaluator → FeedResult with real DB, real scoring, mocked NLP.

**Commits**:
- `test(evaluation): RED tests for FeedEvaluator`
- `feat(evaluation): implement FeedEvaluator with JSON serialization`

---

### Phase 4: Proxy Metrics (2 days)

**Goal**: Compute 8 objective metrics on any `FeedResult`. Used to detect regressions and compare versions without subjective judgment.

**Metrics to implement** (all pure functions of `FeedResult` + corpus):

| Metric name | Formula / computation | Target range |
|---|---|---|
| `topic_entropy` | Shannon entropy of top-3 topic distribution across all 30 items | > 2.0 (balanced) |
| `freshness_median_hours` | Median of `item.age_hours` | < 48 |
| `pairwise_cosine_avg` | Mean of cos(items[i].embedding, items[j].embedding) over all unique pairs | 0.3–0.5 |
| `topic_coverage` | Number of distinct topics represented in items | 4–10 out of 18 |
| `distance_to_profile_avg` | Mean of cos(profile.embedding, item.embedding) | 0.5–0.8 |
| `long_tail_ratio` | % of items from bottom 50% of corpus by source frequency | 15–30% |
| `dedup_pair_count` | Count of pairs where cos > 0.85 (duplicate leakage) | 0 |
| `category_mass_balance` | Gini coefficient of topic distribution | < 0.5 |

**Contract**:

```python
@dataclass(frozen=True)
class ProxyMetrics:
    topic_entropy: float
    freshness_median_hours: float
    pairwise_cosine_avg: float
    topic_coverage: int
    distance_to_profile_avg: float
    long_tail_ratio: float
    dedup_pair_count: int
    category_mass_balance: float

class ProxyMetricsComputer:
    def __init__(self, content_repo: ContentRepository):
        ...

    async def compute(
        self,
        feed: FeedResult,
        user_profile: UserProfile,
    ) -> ProxyMetrics:
        ...
```

**Edge cases** (all must be tested):
- Empty feed → all metrics return NaN with warning, not crash.
- 1-item feed → pairwise_cosine_avg defined as 0.0, dedup_pair_count = 0.
- Missing embeddings on items → compute metric only on items with embeddings, log percentage.

**Implementation location**: `tests/evaluation/proxy_metrics.py`.

**Tests**: `tests/evaluation/tests/test_proxy_metrics.py`:
- Crafted feeds with known properties (e.g., all tech posts → low entropy, single topic → topic_coverage=1).
- Duplicate posts in feed → dedup_pair_count > 0.
- Old posts → freshness_median_hours high.
- Profile vector perfectly matching feed avg → distance_to_profile_avg ~ 1.0.

**Commits**:
- `test(evaluation): RED tests for 8 proxy metrics`
- `feat(evaluation): implement ProxyMetricsComputer`

---

### Phase 5: LLM-as-Judge (2 days)

**Goal**: Use Claude CLI (Haiku model) to score feed relevance from the persona's perspective.

**Contract**:

```python
@dataclass(frozen=True)
class ItemJudgment:
    position: int
    post_id: UUID
    relevance_score: int             # 0-5
    reasoning: str                   # brief, from model

@dataclass(frozen=True)
class JudgeResult:
    persona_id: str
    version: str
    run_number: int
    model: str                       # "haiku"
    total_cost_usd: float
    mean_relevance: float            # avg of all scores
    median_relevance: float
    ndcg_at_10: float                # computed assuming 5=relevant
    judgments: list[ItemJudgment]
    raw_response: str                # full LLM output, for debugging

class LLMJudge:
    def __init__(
        self,
        claude_cli_path: str = "claude",
        model: str = "haiku",
        timeout_s: int = 120,
    ):
        ...

    async def judge(
        self,
        persona: Persona,
        feed: FeedResult,
    ) -> JudgeResult:
        ...
```

**Prompt template** (save as `tests/evaluation/prompts/feed_judge.txt`):

```
You are a recommendation quality judge. You will evaluate how relevant a list of news/content posts are for a specific user persona.

## USER PERSONA

Name: {persona.display_name}
Description: {persona.description}

Interests (weighted): {format_interests(persona.base_interests)}
Avoids: {format_dislikes(persona.dislikes)}
Preferred entities: {', '.join(persona.preferred_entities)}

## FEED (30 items, in recommended order)

{for each item:
1. [{top_topic}] "{title}"
   {snippet_200_chars}
   Age: {age_hours}h | Sentiment: {sentiment}
2. ...
}

## TASK

Rate each item's relevance to this persona on a 0-5 scale:
- 0: Completely irrelevant or in persona's dislike list
- 1: Barely tangential
- 2: Loosely related
- 3: On-topic but generic
- 4: Highly relevant, matches stated interests
- 5: Perfect match — aligns with specific interests, preferred entities, or reading style

Return ONLY a JSON array, no other text:
[
  {"position": 1, "relevance": <0-5>, "reason": "<one sentence>"},
  {"position": 2, "relevance": <0-5>, "reason": "<one sentence>"},
  ...
]
```

**Invocation** (follow cli-delegation skill in SKD root):

```python
async def _call_claude(self, prompt: str) -> str:
    result = subprocess.run(
        [
            self._claude_cli_path, "-p", prompt,
            "--model", self._model,
            "--output-format", "json",
            "--max-turns", "1",
            "--dangerously-skip-permissions",
        ],
        capture_output=True,
        text=True,
        timeout=self._timeout_s,
    )
    payload = json.loads(result.stdout)
    if payload.get("is_error"):
        raise LLMJudgeError(f"Claude CLI error: {payload.get('result')}")
    return payload["result"]  # the raw model output text
```

**Parsing**:
- Strip markdown fences if present.
- Parse JSON.
- Validate: 30 entries, each with position (1-30), relevance (0-5), reason (string).
- On parse failure: retry once with "RETRY: return ONLY valid JSON". After second fail, mark all items as relevance=null and log.

**Cost control**:
- Haiku: ~$0.80/M input + $4/M output (current pricing).
- Per feed: ~8k input (prompt + 30 items × ~200 chars snippet) + 2k output = ~$0.02.
- 20 personas × 3 versions × 3 runs = 180 judgments = **~$4**.
- Set `--max-turns 1` to prevent runaway.
- Log `total_cost_usd` per run to `cost_report.csv`.

**Tests**:
- Unit with mocked subprocess: verify prompt construction, JSON parsing, retry logic.
- Integration (if Haiku accessible during tests): real call with 1 persona + 5-item feed, assert JudgeResult non-empty.
- DO NOT include LLM calls in `uv run pytest` default — gate behind `@pytest.mark.llm` that's opt-in via `pytest -m llm`.

**Commits**:
- `test(evaluation): RED tests for LLMJudge with mocked subprocess`
- `feat(evaluation): implement LLMJudge using claude CLI`

---

### Phase 6: Benchmark Runner (2 days)

**Goal**: CLI orchestrator that runs full pipeline for N personas × M versions × K runs, producing comprehensive artifacts.

**CLI contract** (`scripts/benchmark.py`):

```bash
uv run python scripts/benchmark.py \
    --personas tech-geek,political-junkie,drifting-user \
    --personas-all \                           # or use all 20
    --versions baseline,live_profile \         # config flags, see below
    --runs 3 \                                 # repeats per combination
    --count 30 \                               # feed size
    --seed 42 \                                # base seed
    --enable-llm-judge \                       # opt-in (costs money)
    --judge-model haiku \
    --output .claude/artifacts/eval/benchmarks/2026-04-18_smoke/ \
    --tag smoke \
    --skip-existing                            # resume interrupted run
```

**Version definitions** (`tests/evaluation/versions.yaml`):

```yaml
# Each version = env overrides applied before running GenerateFeedUseCase
versions:
  baseline:
    description: "Current production configuration"
    env: {}
    rec_config_overrides: {}

  live_profile:                                # Phase A from future roadmap
    description: "Live profile recomputation from recent events"
    env:
      REC_FEED_USE_LIVE_PROFILE: "true"
    rec_config_overrides:
      live_profile_blend: 0.6

  rerank:                                      # Phase C from future roadmap
    description: "With cross-encoder reranking"
    env:
      REC_FEED_USE_LIVE_PROFILE: "true"
      REC_RERANK_ENABLED: "true"
    rec_config_overrides:
      rerank_top_k: 100
      rerank_weight: 0.5
```

For THIS project scope, only `baseline` is required. `live_profile` and `rerank` are placeholders — the harness supports them, their implementations come later.

**Execution flow per (persona, version, run) combo**:

```python
async def run_combination(persona, version, run_number, seed):
    # 1. Apply version config (env vars, rec_config)
    apply_version(version)

    # 2. Reset synthetic user state (clean schema in test DB)
    user_id = uuid5(NAMESPACE, f"{persona.id}:{version}:{run_number}")
    await reset_user(user_id)

    # 3. Simulate behavior
    sim_state = await simulator.simulate(persona, user_id, seed=seed + run_number)

    # 4. Generate feed
    feed = await feed_evaluator.run(sim_state, count=30)

    # 5. Compute proxy metrics
    proxy = await metrics_computer.compute(feed, sim_state.profile_snapshot)

    # 6. LLM judge (if enabled)
    judge_result = None
    if enable_llm:
        judge_result = await llm_judge.judge(persona, feed)

    # 7. Save artifacts
    save_feed_json(feed, output_dir)
    save_judge_json(judge_result, output_dir)

    return CombinationResult(persona, version, run_number, feed, proxy, judge_result)
```

**Artifacts produced** (in `.claude/artifacts/eval/benchmarks/{timestamp}_{tag}/`):

- `summary.md` — top-level report with:
  - Executive summary table: (persona × version) → mean LLM score, proxy metric deltas
  - Per-version aggregated metrics
  - Persona rankings per version
  - Cost totals ($ spent on LLM)
  - Run config
- `metrics.csv` — flat table: one row per (persona, version, run), all proxy metrics + LLM mean + LLM nDCG@10
- `personas_snapshot.yaml` — copy of all personas used (for reproducibility — personas may change over time)
- `config_snapshot.yaml` — `rec_config` rows at time of run + env vars
- `versions_used.yaml` — which versions ran, their configs
- `feeds/{persona}_{version}_run{N}.json` — full FeedResult
- `llm_judgments/{persona}_{version}_run{N}.json` — full JudgeResult
- `costs.csv` — LLM costs per judgment
- `errors.log` — any failures, with traceback

**Summary.md template** (`tests/evaluation/templates/summary.md.j2`):

```markdown
# Benchmark: {tag}

**Run date**: {timestamp}
**Duration**: {duration_minutes} min
**Total cost**: ${total_cost:.2f}
**Combinations**: {n_personas} personas × {n_versions} versions × {n_runs} runs = {total_runs}

## Executive Summary

{if multiple versions}
| Persona | baseline LLM avg | live_profile LLM avg | Δ |
|---------|------------------|----------------------|---|
...
{else}
| Persona | LLM avg | nDCG@10 | Topic entropy | Freshness (h) |
|---------|---------|---------|---------------|---------------|
...

## Winners and regressions

...

## Per-version detailed tables

{for each version}:
### {version}

| Persona | LLM mean | LLM median | nDCG@10 | Entropy | Coverage | Distance | Dedup pairs |
...

## Cost breakdown

Model: {judge_model}
Total tokens: input={input_tokens}, output={output_tokens}
Cost: ${total_cost:.2f}

## Known issues

{list of errors if any}

## Reproducibility

- Seed: {seed}
- rec-system commit: {git_commit}
- rec_config snapshot: config_snapshot.yaml
- Personas snapshot: personas_snapshot.yaml

To reproduce: `uv run python scripts/benchmark.py --tag {tag} --seed {seed} ...`
```

**Tests**:
- Integration: run benchmark with 2 personas, baseline only, 1 run, `--enable-llm-judge=false`. Verify artifacts created, metrics.csv has 2 rows, summary.md not empty.
- End-to-end smoke: full 3 personas × 1 version × 2 runs with `--enable-llm-judge=false` (LLM opt-in) — must finish < 10 min.

**Commits**:
- `test(evaluation): RED integration tests for benchmark runner`
- `feat(evaluation): implement BenchmarkRunner and CLI orchestrator`
- `feat(evaluation): add Jinja2 templates for summary.md and CSV writers`

---

### Phase 7: Playground Notebook (2 days)

**Goal**: Extend existing `rec_playground.ipynb` (or create `notebooks/rec_playground.ipynb`) with interactive widgets for qualitative assessment.

**Features**:

1. **Setup cell** — connect to eval DB, load persona library, load rec-system services.
2. **Persona explorer cell** — dropdown with all 20 personas, shows description + interests radar chart.
3. **Version selector** — multi-select: baseline, live_profile, rerank.
4. **"Generate feed" button** — for selected persona, runs simulator + evaluator for each version.
5. **Side-by-side rendering** — N columns (one per version):
   - Each column: ordered list of feed items (position, title, snippet, topic tag, age)
   - Color coding: green = top-5, yellow = 5-15, white = 15-30
   - Click on item → pops up explainability panel (Phase 8)
6. **Diff view** — highlight which posts appear in version A but not B, and vice versa. Show rank changes (↑12, ↓3).
7. **Metrics table** — proxy metrics per version, side-by-side.
8. **LLM judge panel** — optional button "Run LLM judge" — calls Haiku, shows per-position relevance scores + reasoning, aggregate stats.
9. **Persona evolution trace** — for drift persona, show how `profile.topic_vector` changes across simulation timeline.

**Implementation** (use `ipywidgets`):

```python
# Cell 1: imports and setup
import ipywidgets as widgets
from IPython.display import display, HTML, clear_output
# ... load eval framework, connect to DB

# Cell 2: persona selector
persona_dropdown = widgets.Dropdown(
    options=[(p.display_name, p.id) for p in personas],
    description='Persona:',
)

# Cell 3: version selector
version_check = widgets.SelectMultiple(
    options=['baseline', 'live_profile', 'rerank'],
    value=['baseline'],
    description='Versions:',
)

# Cell 4: run button + output
run_button = widgets.Button(description='Generate Feeds')
output = widgets.Output()

def on_run(b):
    with output:
        clear_output()
        # for each selected version, simulate + evaluate + display
        render_side_by_side(persona, selected_versions)

run_button.on_click(on_run)
display(persona_dropdown, version_check, run_button, output)
```

**Rendering helpers** in `notebooks/rec_playground_helpers.py`:
- `render_side_by_side(persona, versions)` — HTML table with columns
- `render_explainability(feed_item)` — bar chart of scoring components
- `render_diff_matrix(feeds)` — which posts appear where

**Deliverable checklist**:
- [ ] Notebook runs top-to-bottom without errors on fresh kernel.
- [ ] All widgets functional.
- [ ] README cell explains how to use.
- [ ] Screenshots saved to `.claude/artifacts/eval/playground_screenshots/` for documentation.

**Tests**: not strictly unit-testable (notebooks are hard). Instead:
- `test_notebook_smoke.py` — uses nbconvert to execute notebook, asserts exit code 0.
- `test_render_helpers.py` — unit tests of pure rendering functions.

**Commits**:
- `feat(notebooks): extend rec_playground with side-by-side comparison widgets`
- `feat(notebooks): add explainability visualization in playground`
- `test(notebooks): add notebook smoke test via nbconvert`

---

### Phase 8: Explainability Endpoint (1–2 days)

**Goal**: Dev-only endpoint `POST /recommendations/explain` returning per-component scoring breakdown for a (user, post) pair. Used by playground and for debugging.

**Contract**:

```python
# Request
{
  "user_id": "uuid",
  "content_id": "uuid"   # published_content.id
}

# Response
{
  "content_id": "uuid",
  "raw_content_id": "uuid",
  "final_score": 0.78,
  "components": {
    "topic_match": {
      "value": 0.85,
      "weight": 0.30,
      "contribution": 0.255,
      "matched_topics": [
        {"topic": "технологии", "profile_weight": 0.45, "post_score": 0.92},
        {"topic": "наука", "profile_weight": 0.30, "post_score": 0.67}
      ]
    },
    "embedding_sim": {
      "value": 0.62,
      "weight": 0.25,
      "contribution": 0.155
    },
    "entity_match": {
      "value": 0.33,
      "weight": 0.15,
      "contribution": 0.050,
      "matched_entities": ["OpenAI", "GPT-5"],
      "interest_weights": {"OpenAI": 2.3, "GPT-5": 1.1}
    },
    "sentiment_match": {
      "value": 0.50,
      "weight": 0.05,
      "contribution": 0.025,
      "post_sentiment": "NEUTRAL",
      "profile_preference": {"POS": 0.3, "NEG": 0.3, "NEU": 0.4}
    },
    "freshness": {
      "value": 0.89,
      "weight": 0.15,
      "contribution": 0.134,
      "age_hours": 2.3,
      "half_life_hours": 48
    },
    "format_match": {
      "value": 0.70,
      "weight": 0.10,
      "contribution": 0.070,
      "post_word_count": 520,
      "profile_preferred_word_count_avg": 480,
      "post_complexity": 0.65,
      "profile_preferred_complexity_avg": 0.72
    }
  },
  "cold_start_applied": false,
  "dead_components": [],
  "redistributed_weights": null,
  "rank_in_candidates": 12,
  "rank_in_feed": 3,
  "filtered_out_by": null,    // or "diversity_streak", "duplicate_cluster", "recommendation_history"
  "dedup_cluster_id": 42,
  "published_content_id": "uuid"
}
```

**Implementation**:

1. New service `src/application/services/scoring_explainer.py`:
   - Wraps existing `ScoringService`
   - Instead of returning just final_score, exposes per-component internals
   - Computes contribution = value × weight per component
   - Handles cold-start weight redistribution transparently

2. New route `src/presentation/api/routes/explain.py`:
   - Gated behind env var `REC_EXPLAIN_ENABLED=true` (dev-only)
   - Returns 404 in production
   - Accepts POST with user_id + content_id
   - Returns JSON as above

3. Modify `src/application/use_cases/generate_feed.py` to optionally capture rank+filtering info into a per-item trace (only when explain mode enabled, otherwise no perf impact).

**Risk mitigation**:
- NEVER return this response in production. Default `REC_EXPLAIN_ENABLED=false`.
- Log warning on first call so it's visible in logs.
- Return 403 Forbidden if env var not set.

**Tests**:
- Unit: ScoringExplainer returns correct contribution = value × weight for all 6 components.
- Integration: full endpoint call with seeded user and post, assert JSON shape matches schema.
- Verify cold-start case: dead component weights redistributed; response notes which components were dead.

**Commits**:
- `test(presentation): RED tests for explainability endpoint`
- `feat(application): implement ScoringExplainer service`
- `feat(presentation): add POST /recommendations/explain dev endpoint`

---

## 7. Artifacts Structure

```
.claude/artifacts/eval/
├── README.md                                    # Entry point. How to run, how to read.
├── personas_registry.md                         # Catalog of all personas (human-readable).
├── personas_changelog.md                        # Track persona changes over time.
├── benchmarks/
│   └── 2026-04-18_baseline/                     # One directory per benchmark run
│       ├── summary.md                           # Human-readable top-level report
│       ├── metrics.csv                          # Flat metrics table
│       ├── costs.csv                            # LLM costs breakdown
│       ├── errors.log                           # Failures during run
│       ├── personas_snapshot.yaml               # Personas used (pinned)
│       ├── config_snapshot.yaml                 # rec_config + env at runtime
│       ├── versions_used.yaml                   # Which versions ran
│       ├── feeds/
│       │   ├── tech-geek_baseline_run1.json     # FeedResult
│       │   ├── tech-geek_baseline_run2.json
│       │   └── ... (one per combination)
│       └── llm_judgments/
│           ├── tech-geek_baseline_run1.json     # JudgeResult
│           └── ...
├── comparisons/
│   └── 2026-04-19_baseline_vs_live_profile/     # Generated by compare_benchmarks.py
│       ├── summary.md                           # Δ per metric per persona
│       ├── diff.csv
│       └── winners_losers.md
├── playground_screenshots/                      # Screenshots of notebook for docs
│   └── side_by_side_tech_geek.png
└── reports/                                     # Ad-hoc analysis reports
    └── cold_start_analysis_2026-04-20.md
```

### Every benchmark artifact MUST include

- **Timestamp** in directory name (unique)
- **Tag** (human-readable purpose)
- **Git commit** of rec-system at run time
- **All input config** (personas, versions, rec_config)
- **All output** (feeds, judgments, metrics)

Never rely on "the benchmark I ran last Tuesday" — always include full provenance.

### README.md template

```markdown
# Rec-System Evaluation Harness

## Quick start

```bash
cd /home/mattew/SKD/rec-system

# Run a smoke test (3 personas, no LLM)
uv run python scripts/benchmark.py \
    --personas tech-geek,political-junkie,balanced-reader \
    --versions baseline \
    --runs 2 \
    --tag smoke

# Full benchmark with LLM judge
uv run python scripts/benchmark.py \
    --personas-all \
    --versions baseline \
    --runs 3 \
    --enable-llm-judge \
    --tag full_v1
```

## Reading results

Open `.claude/artifacts/eval/benchmarks/{ts}_{tag}/summary.md`.

Key metrics to watch:
- **LLM mean relevance** > 3.5 is good, > 4.0 excellent
- **Topic entropy** > 2.0 = diverse, < 1.5 = mono-topic bias
- **Distance to profile avg** 0.5–0.8 = sweet spot
- **Dedup pair count** > 0 = leak in dedup logic

## Comparing versions

```bash
uv run python scripts/compare_benchmarks.py \
    --baseline {path_A} \
    --candidate {path_B} \
    --output comparisons/$(date +%Y-%m-%d)_A_vs_B
```

## Playground

```bash
cd rec-system && uv run jupyter notebook notebooks/rec_playground.ipynb
```

## Architecture

See `rec-system-eval-harness-spec.md` at the repository root.
```

---

## 8. Testing Strategy

**Per rec-system CLAUDE.md** — strict TDD, `uv run pytest`, all tests green before commit.

### Test pyramid

```
E2E:              2-3 tests (smoke benchmark run, notebook execution)
Integration:      15-20 tests (simulator + DB, evaluator + DB, benchmark runner)
Unit:             60+ tests (personas, metrics, judge, explainer, rendering)
```

### Markers

- `@pytest.mark.unit` — default, always run.
- `@pytest.mark.integration` — testcontainers PG + Kafka.
- `@pytest.mark.llm` — opt-in, calls real Claude CLI (costs money).
- `@pytest.mark.slow` — opt-in, > 30s tests.

### CI-friendly run

```bash
# Fast — run on every commit
uv run pytest -m "unit or (integration and not slow)" --tb=short

# Full — run before merge
uv run pytest -m "not llm"

# LLM gated — run manually
uv run pytest -m llm
```

### Coverage

- New code: ≥ 90% line coverage (rec-system convention).
- Use `uv run pytest --cov=tests.evaluation --cov-report=html`.
- Aim for ≥ 95% on critical paths (metrics, judge, simulator).

---

## 9. Dependencies

To be added via `uv add`:

```toml
[project.optional-dependencies]
eval = [
    "pyyaml>=6.0",                 # Persona YAML loading
    "jinja2>=3.1",                 # Report templates
    "pandas>=2.0",                 # CSV + DataFrame for metrics
    "jupyter>=1.0",                # Notebook (dev-only)
    "ipywidgets>=8.0",             # Interactive widgets in notebook
    "nbconvert>=7.0",              # Notebook smoke testing
    "scipy>=1.11",                 # Entropy, Gini
]
```

**DO NOT** add anything to core dependencies. Eval is dev/test only.

Install:
```bash
uv add --optional eval pyyaml jinja2 pandas jupyter ipywidgets nbconvert scipy
```

---

## 10. Execution Checklist (per-phase sub-claude MUST run; orchestrator verifies)

Sequential within each phase — DO NOT skip steps. Each checkpoint = commit boundary. Orchestrator uses this list to verify sub-claude actually did the work.

### Prep

- [ ] `cd /home/mattew/SKD/rec-system` and run `git status` — ensure clean working tree.
- [ ] Run `uv sync` — ensure environment current.
- [ ] Run `uv run pytest -m unit --tb=no -q` — establish baseline, record # passing.
- [ ] Create branch: `git checkout -b feat/eval-harness`.

### Phase 0 (required)

- [ ] Verify CSV at `/home/mattew/SKD/telegram_posts.csv` exists (ls -la).
- [ ] Compute CSV stats (row count, channel distribution, date range) → save to `.claude/artifacts/eval/csv_stats.md`.
- [ ] Create `config.sources` row for `eval_telegram_import`, capture UUID.
- [ ] Write `scripts/ingest_telegram_csv.py` with TDD (RED fixture tests → GREEN).
- [ ] Run ingestion with `--limit 30000 --sample-strategy stratified_by_channel`.
- [ ] Verify ≥ 25 000 rows landed in raw_content.
- [ ] Tune env: `REC_CONTENT_BATCH_SIZE=200, REC_CONTENT_JOB_INTERVAL_SECONDS=20` in docker-compose.ml.yml. Rebuild/redeploy rec-worker (and dedup equivalent).
- [ ] Poll backlog every 5 min until both `is_processed_by_rec=false` AND `is_processed_by_dedup=false` for telegram-source reach 0.
- [ ] Revert env overrides.
- [ ] Write `.claude/artifacts/eval/corpus_snapshot.md` with final state (total posts, channels, topic distribution from posts_features).

### Phase 1 — Persona Framework

- [ ] RED: write `test_persona_loader.py` covering load/validate/edge cases.
- [ ] Verify tests fail (no implementation yet).
- [ ] Commit: `test(evaluation): RED tests for persona loader`.
- [ ] GREEN: implement `models.py`, `persona_loader.py`.
- [ ] Write 20 persona YAMLs (use template; ensure diversity per table).
- [ ] All tests pass. Commit: `feat(evaluation): persona framework and 20 personas`.
- [ ] Generate `personas_registry.md`.

### Phase 2 — Behavior Simulator

- [ ] RED: integration tests for 5 representative personas (tech-geek, political-junkie, cold-start-empty, drifting, balanced-reader).
- [ ] Commit RED.
- [ ] GREEN: implement simulator. Use existing OnboardUserUseCase + UpdateProfileUseCase.
- [ ] Verify determinism (same seed = same state).
- [ ] All tests pass. Commit.

### Phase 3 — Feed Evaluator

- [ ] RED: unit test for FeedResult serialization, integration test for full path.
- [ ] GREEN: implement FeedEvaluator (thin wrapper over GenerateFeedUseCase).
- [ ] Commit.

### Phase 4 — Proxy Metrics

- [ ] RED: unit tests with hand-crafted feeds (known entropy, known cosine).
- [ ] GREEN: implement 8 metrics.
- [ ] Commit.

### Phase 5 — LLM Judge

- [ ] RED: unit tests with mocked subprocess.
- [ ] GREEN: implement LLMJudge with Haiku.
- [ ] Test real call once (manual) with 1 persona + 5 items. Confirm cost < $0.05.
- [ ] Commit.

### Phase 6 — Benchmark Runner

- [ ] RED: integration test for smoke run (2 personas × baseline × 1 run, no LLM).
- [ ] GREEN: implement runner + CLI + templates.
- [ ] Run `uv run python scripts/benchmark.py --personas tech-geek --versions baseline --runs 1 --tag dev_smoke --enable-llm-judge=false`.
- [ ] Verify artifacts created in `.claude/artifacts/eval/benchmarks/`.
- [ ] Commit.

### Phase 7 — Playground Notebook

- [ ] Extend or create notebook.
- [ ] Add rendering helpers.
- [ ] Smoke test via nbconvert.
- [ ] Save screenshot.
- [ ] Commit.

### Phase 8 — Explainability Endpoint

- [ ] RED: unit tests for ScoringExplainer.
- [ ] GREEN: implement service + endpoint + env gating.
- [ ] Commit.

### Final

- [ ] Run full benchmark once: all 20 personas × baseline × 3 runs with LLM judge.
- [ ] Budget: should cost < $10 total, take < 30 min.
- [ ] Save artifacts to `.claude/artifacts/eval/benchmarks/{ts}_initial_baseline/`.
- [ ] Update `.claude/artifacts/eval/README.md` with this run as reference.
- [ ] Run `uv run pytest` — all tests green.
- [ ] Final commit: `feat(evaluation): complete eval harness v1 with initial baseline report`.
- [ ] PR-style summary in the branch description.

---

## 11. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| LLM judge inconsistent across runs | Medium | Metrics noisy | Use temperature=0 if available; average 3+ runs; treat LLM as rough signal, not truth |
| Personas overfit to benchmark | Medium | False confidence | Diverse personas (20+), periodically refresh, supplement with proxy metrics |
| Benchmark writes to production DB | Low | Data corruption | Enforce schema separation; refuse to run if DB URL contains "prod"; integration tests use testcontainers |
| Synthetic user sampling bias | High | Unrealistic state | Document assumptions; validate sim state manually for 3 personas initially |
| Claude CLI unavailable/API changes | Low | Pipeline breaks | Abstract behind interface; unit tests mock subprocess; integration tests gated |
| Eval harness makes rec-system slower | Low | Dev friction | All eval code in `tests/` — zero production impact |
| Content corpus insufficient diversity | Medium | Metrics don't discriminate | Phase 0 ingests additional 20k posts if CSV available; document corpus stats in every benchmark |
| Playground becomes maintenance burden | Medium | Gets stale | Smoke test in CI; separate helpers module; don't embed logic in notebook |

---

## 12. Post-delivery — how to use this harness

### First benchmark (baseline)

After completion, agent runs full baseline benchmark:

```bash
uv run python scripts/benchmark.py --personas-all --versions baseline --runs 3 \
    --enable-llm-judge --tag initial_baseline \
    --output .claude/artifacts/eval/benchmarks/initial_baseline/
```

This becomes the **reference point** for all future changes.

### Workflow for any rec-system change

1. Before change: run benchmark, tag `pre_{change_description}`.
2. Make change.
3. After change: run benchmark, tag `post_{change_description}`.
4. `uv run python scripts/compare_benchmarks.py --baseline pre_X --candidate post_X --output ...`
5. Review `.claude/artifacts/eval/comparisons/{ts}_pre_X_vs_post_X/summary.md`.
6. Accept if: LLM mean ≥ baseline AND no proxy metric regresses by > 10%.
7. Reject and iterate if: any metric worsens significantly.

### When to add new personas

- When a real user behavior emerges that's not covered.
- When a new feature (e.g., semantic search) requires test cases.
- When a regression was found that current personas missed.

Bump `personas_changelog.md` with reason.

### When to refresh personas

- Every 6 months minimum.
- If real user analytics show personas miss key behaviors.
- Keep old personas file-archived for reproducibility of past benchmarks.

---

## 13. References

- rec-system CLAUDE.md: `/home/mattew/SKD/rec-system/CLAUDE.md`
- Design docs: `/home/mattew/SKD/rec-system/design/`
- Current scoring formula: `/home/mattew/SKD/rec-system/src/domain/services/scoring_service.py`
- Current feed pipeline: `/home/mattew/SKD/rec-system/src/application/use_cases/generate_feed.py`
- Existing playground: `/home/mattew/SKD/rec-system/rec_playground.ipynb`
- SKD-level CLI delegation skill: `/home/mattew/SKD/.claude/skills/cli-delegation/`
- Similar eval harnesses for inspiration:
  - Cohere Rerank evaluation blog
  - BEIR benchmark paper (https://github.com/beir-cellar/beir)
  - RAGAS framework (https://github.com/explodinggradients/ragas)

---

## End of specification

**SKD orchestrator**: your first action is to read Section 5 (Orchestration Model) end-to-end, then follow the loop:

1. Create branch `feat/eval-harness` in `/home/mattew/SKD/rec-system/` (if not exists).
2. Initialize `/home/mattew/SKD/.claude/artifacts/rec-eval/orchestration_state.md` with status table (all phases queued).
3. For phase N in 0..8:
   - Build prompt from template in §5.3 using the phase section from §6.
   - Invoke `claude -p` per §5.4 from `rec-system/` directory.
   - Verify return per §5.5.
   - Update tracker.
   - On FAILED/BLOCKED: follow §5.6.
4. After Phase 8: run smoke benchmark per §5.8, produce `final_report.md`.

**rec-system sub-claude** (if ever invoked directly): read only the phase-specific section in §6 that you're told to execute. The full spec is for orchestrator reference — you do ONE phase per session.

CSV is at `/home/mattew/SKD/telegram_posts.csv` — no search needed.

Good luck. Build a benchmark you can trust.
