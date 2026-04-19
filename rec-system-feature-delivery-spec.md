# Technical Specification: Rec-System Feature Delivery (Polish + Phase A/B/C)

**Project**: `rec-system` (Python FastAPI recommendation engine)
**Version**: 1.0
**Created**: 2026-04-18
**Target project directory**: `/home/mattew/SKD/rec-system/`
**Companion doc**: `/home/mattew/SKD/rec-system-eval-harness-spec.md` (evaluation harness, ALREADY COMPLETED on branch `feat/eval-harness`)
**Estimated effort**: 8–12 рабочих дней (1 engineer)
**Execution mode**: SKD orchestrator → fresh `claude -p` sub-sessions (same pattern as eval harness project)

---

## 0. Context (must-read before starting)

### Prerequisites — what must be true when this project starts

- Branch `feat/eval-harness` exists in `/home/mattew/SKD/rec-system/` with 26 commits (tip `86b816c` or later). NOT YET merged to master.
- Orchestration history of the prior project saved in `/home/mattew/SKD/.claude/artifacts/rec-eval/`:
  - `orchestration_state.md` — tracker from harness work
  - `final_report.md` — what shipped, what issues remain
  - `phase{0..8}_prompt.txt` — reference prompts
- Initial baseline benchmark running (or completed) at `/home/mattew/SKD/rec-system/.claude/artifacts/eval/benchmarks/2026-04-18_initial_baseline/`. By the time this project starts, it SHOULD be complete (20 personas × 3 runs × LLM judge). Orchestrator's first action is to verify.
- rec-worker container is running with current scheduler (batch=50, interval=30s). VRAM budget on 5060 Ti: rec ~1.1 GB, dedup ~2.8 GB, codebase-rag ~5.6 GB, total ~10.5 GB of 16 GB.
- ~29 000 telegram posts in `data_flow.raw_content` from Phase 0 of harness project, all processed (`is_processed_by_rec=true`, `is_processed_by_dedup=true`).

### Known issues from the eval-harness project to address

Per eval-harness `final_report.md` and the COMPLETED `initial_baseline` benchmark (60/60 feeds, 57/60 judgments — see artifacts at `/home/mattew/SKD/rec-system/.claude/artifacts/eval/benchmarks/2026-04-18_initial_baseline/`):

1. **pgvector search_path bug in main app engine**: `scripts/benchmark.py` has the fix (commit `86b816c`). Same bug suspected in `src/infrastructure/container.py` — if anything else creates a fresh engine, pgvector operators won't resolve. **Scope**: Phase P1.

2. **Dedup pair count > 0 in feed**: signal from proxy metrics that retrieval returns near-duplicates. **Scope**: Phase P2 investigation. Root cause unknown — see P2 for hypotheses.

3. **`freshness_median = 443 hours` in M0 baseline** (target < 48h): feeds are dominated by OLD content. `ranking_params.max_age = 7d = 168h` but median is 2.6× over it. Possible causes: retrieval bypass of `max_age` filter, CSV ingestion set `processed_at`/`post_date` to stale values, or corpus genuinely lacks fresh content. **Scope**: investigation in Phase P2 (added as second investigation target).

4. **LLM judge timeouts (3 / 60 combinations)**: `timeout_s = 120` is tight. Three persona-run pairs timed out, leaving 57/60 judgments and contaminating M0 aggregate. Must be fixed BEFORE M1/M2/M3 benchmarks to ensure clean 60/60 data for comparisons. **Scope**: new Phase P2b.

5. **`entity-focused-musk` LLM mean = 0.31** (catastrophically low): NER extracts inconsistent entity names ("Илон Маск" vs "И. Маск" vs "Elon Musk") — `entity_match` component can't match them. This is a known limitation documented in rec-system CLAUDE.md Known Limitations. **Not in scope** — entity normalization requires substantial new infrastructure. Phase C (cross-encoder) may partially mitigate by matching on text content rather than exact entity strings. Document and monitor.

6. **`sports-fan` and `health-focused` LLM means < 1.2**: niche-topic personas suffer because corpus is news-general (Telegram dominated by mainstream topics). Phase C may help modestly via better semantic matching, but fundamental limitation is corpus composition. **Not in scope to fix corpus**. Document. When interpreting M1/M2/M3 comparisons, FOCUS ON **relative delta per persona** (did X improve between milestones) rather than absolute scores.

7. **Phase 1 artifacts in wrong dir**: `tests/evaluation/artifacts/personas_registry.md` instead of `.claude/artifacts/eval/personas_registry.md`. Cosmetic — leave as-is.

### Baseline reference (M0)

- Source: `2026-04-18_initial_baseline` completed 2026-04-18 23:xx
- **LLM mean across all personas** (57/60 judged): ~X.XX (orchestrator must compute from metrics.csv)
- **Valid personas for comparisons** (excluding 3 timeout failures): 17 of 20
- After Phase P2b (fix timeout), orchestrator must re-run the 3 missing combinations to restore 60/60 M0 before proceeding to Phase A.

### What exists in rec-system NOW

From harness project:
- 20 personas in `tests/evaluation/personas/*.yaml`
- `BehaviorSimulator`, `FeedEvaluator`, `ProxyMetricsComputer`, `LLMJudge`, `BenchmarkRunner` in `tests/evaluation/`
- `scripts/benchmark.py`, `scripts/ingest_telegram_csv.py`
- `notebooks/rec_playground.ipynb` + helpers
- `POST /recommendations/explain` dev endpoint (403-gated)
- `ScoringExplainer` in `src/application/services/`
- Additive methods on `ContentRepository` interface + impl

From master/base:
- `GenerateFeedUseCase` with 6-component scoring, two-stage retrieval (freshness + embedding), diversity filter, dedup clustering, history exclusion, published-ID mapping
- `OnboardUserUseCase`, `UpdateProfileUseCase`, `ProcessContentUseCase`, `HandleInteractionsBatchUseCase`
- NLP adapters: `TorchTopicClassifier`, `TorchSentimentAnalyzer`, `SpacyEntityExtractor`, `TorchContentEncoder` (rubert-tiny2, 312-dim)
- `ScoringService` in domain with 6 components
- DI container with `dependency-injector`

### Goal of this project

Ship three real improvements to recommendation quality (Phase A/B/C) **after** polishing existing technical debt, with **objective before/after measurements** at every step. Final deliverable: 4 benchmark milestones (M0 baseline, M1 +A, M2 +A+B, M3 +A+B+C) with statistical deltas showing direction and magnitude of each improvement.

## 1. Goals

1. Close the technical debt from harness project (pgvector fix, dedup investigation, merge).
2. Implement **Phase A — Live profile recomputation**: rec_profiles embedding combined with recent positive interactions at request time.
3. Implement **Phase B — On-arrival hot-content encoding**: new posts get full NLP features within seconds instead of 30-second scheduler cycle.
4. Implement **Phase C — Cross-encoder reranking**: second-stage ranking of top-N candidates using a pretrained multilingual reranker.
5. Measure each addition with identical benchmark methodology. Produce comparison reports.
6. Remain fully backward-compatible: feature flags for A/B/C, default off, individually toggleable.

## 2. Non-goals (explicit)

- ❌ NOT retraining any existing model (topic classifier, sentiment, encoder).
- ❌ NOT fine-tuning cross-encoder on custom data (use pretrained).
- ❌ NOT modifying scoring formula weights (only add reranking on top).
- ❌ NOT redesigning retrieval (pgvector bi-encoder retrieval stays).
- ❌ NOT touching parser-service, dedup-system, backend microservices, frontend.
- ❌ NOT implementing A/B testing at user level (that's for post-user-rollout).
- ❌ NOT building streaming Kafka pipelines for interactions (Phase A is request-time, Phase B is arrival-time only).
- ❌ NOT moving dedup to Quadro GPU (infra task tracked separately).

## 3. Success Criteria (acceptance)

Orchestrator MUST deliver:

- [ ] All polish phases (P1-P4) completed, master branch has harness + fixes merged.
- [ ] Four benchmark milestones exist with complete LLM judge data:
  - M0_baseline (existing `2026-04-18_initial_baseline/` if valid, else fresh run)
  - M1_phase_a
  - M2_phase_a_b
  - M3_phase_a_b_c
- [ ] Each milestone: 20 personas × 3 runs × count=30 × LLM judge. Same seed (42). Same corpus snapshot.
- [ ] `scripts/compare_benchmarks.py` producing side-by-side delta reports between any two milestones.
- [ ] `.claude/artifacts/rec-eval/delivery_final_report.md` with:
  - Per-milestone mean LLM score, nDCG@10, 8 proxy metrics
  - Persona-level winners/losers per transition (M0→M1, M1→M2, M2→M3)
  - Statistical significance (Wilcoxon signed-rank on paired persona scores)
  - Total cost breakdown
- [ ] All feature flags documented: `REC_FEATURE_LIVE_PROFILE`, `REC_FEATURE_HOT_ARRIVAL`, `REC_FEATURE_RERANK`. Each defaults to `false`. Each toggleable independently.
- [ ] Full test suite green: `uv run pytest -m "unit or integration"` all pass; no regressions vs eval-harness baseline.
- [ ] VRAM budget respected: rec-worker steady-state < 4 GB, peak < 7 GB under concurrent load.
- [ ] Feed latency acceptable: baseline p95 < 200ms, with all features on p95 < 700ms.

Functional smoke test (must pass):
```bash
cd /home/mattew/SKD/rec-system
# All features OFF — equivalent to master
REC_FEATURE_LIVE_PROFILE=false REC_FEATURE_HOT_ARRIVAL=false REC_FEATURE_RERANK=false \
  uv run python scripts/benchmark.py --personas tech-geek,political-junkie --versions baseline --runs 1 --tag smoke_off --db-url <live>
# All features ON
REC_FEATURE_LIVE_PROFILE=true REC_FEATURE_HOT_ARRIVAL=true REC_FEATURE_RERANK=true \
  uv run python scripts/benchmark.py --personas tech-geek,political-junkie --versions rerank --runs 1 --tag smoke_on --db-url <live>
```
Both return 0, produce non-empty feeds.

## 4. Orchestration Model (same as eval-harness project — see that spec §5 for details)

### 4.1. Progress tracker

Location: `/home/mattew/SKD/.claude/artifacts/rec-eval/delivery_state.md`
Append-only. Mirror schema of harness `orchestration_state.md` but with new phase list (see §5).

### 4.2. Fresh sub-claude per phase

Each phase = one `claude -p` invocation from `/home/mattew/SKD/rec-system/`. Orchestrator never writes code.

### 4.3. Prompt template (reuse from harness spec §5.3)

Each phase prompt includes:
1. **Current progress summary** (what's done)
2. **Full phase section** from this spec copy-pasted
3. **Execution rules** (TDD, uv run, branch, no scope creep)
4. **References** (this spec, prior artifacts, CLAUDE.md)
5. **Return format** (`### PHASE {X} RESULT` with STATUS, commits, tests, artifacts)

### 4.4. Invocation

```bash
cd /home/mattew/SKD/rec-system && claude -p "$PROMPT" \
    --dangerously-skip-permissions \
    --output-format json \
    --max-turns <per-phase> \
    --model sonnet \
    > /tmp/rec-delivery-phase-{X}.json 2>&1
```

### 4.5. Verification (same 7 steps as harness §5.5)

After each sub-claude returns:
1. Parse JSON, check `is_error=false`
2. Verify commits via `git log`
3. Smoke test: `uv run pytest tests/evaluation -m unit --tb=no -q` — must stay green
4. Verify artifacts at expected paths
5. Update `delivery_state.md`
6. Commit tracker state at SKD level
7. Summary to user

### 4.6. Cost budget

- Per phase cap: **$20** (larger than harness because Phase C includes 50+ tests and model integration)
- Total cap: **$150**
- Orchestrator asks user at 80% utilization.

### 4.7. Branch strategy

- Start: `feat/eval-harness` tip (26 commits)
- Polish phases (P1–P3): commit directly to `feat/eval-harness` — extends the eval-harness branch
- After P3: `git merge feat/eval-harness --no-ff` into master in P4
- Feature phases (A, B, C): commit to new branch `feat/rec-improvements` off master
- Each feature phase gets its own milestone commit (`M1_phase_a` tag), but all on same branch
- Final merge to master: after M3 benchmark validates improvements

---

## 5. Phases (ordered execution)

### Legend
- **P0–P4**: Polish / pre-work
- **A, B, C**: Feature phases
- **M0, M1, M2, M3**: Benchmark milestones (run by orchestrator, not by sub-claude)

Dependency graph:

```
P0 (verify state)
  ↓
P1 (pgvector fix in main container)
  ↓
P2 (investigation: dedup leak + freshness 443h)
  ↓
P2b (LLM judge reliability: timeout + retry)
  ↓
P3 (re-run missing 3 M0 judgments, restore 60/60 baseline)
  ↓
P4 (merge feat/eval-harness → master, start feat/rec-improvements)
  ↓
Phase A (live profile) → M1 benchmark → compare m0_vs_m1
  ↓
Phase B (hot arrival) → M2 benchmark → compare m1_vs_m2
  ↓
Phase C (cross-encoder) → M3 benchmark → compare m2_vs_m3, m0_vs_m3
  ↓
Final (delivery_final_report.md with all deltas)
```

---

### Phase P0: Verify State (0.5 day — mostly checks)

**Goal**: confirm everything expected is in place before starting real work.

**Checks**:

1. **Branch state**:
   ```bash
   cd /home/mattew/SKD/rec-system
   git status           # must be clean
   git branch --show-current   # must be feat/eval-harness (or can be checked out)
   git log --oneline master..feat/eval-harness | wc -l   # ≥ 25
   ```
2. **Tests green**:
   ```bash
   uv run pytest -m "unit" --tb=no -q
   ```
   All pass. Record exact count to `delivery_state.md`.
3. **Initial baseline benchmark state**:
   ```bash
   ls .claude/artifacts/eval/benchmarks/2026-04-18_initial_baseline/feeds/ | wc -l
   ls .claude/artifacts/eval/benchmarks/2026-04-18_initial_baseline/llm_judgments/ | wc -l
   cat .claude/artifacts/eval/benchmarks/2026-04-18_initial_baseline/summary.md  # exists?
   ```
   If feeds = 60 AND judgments = 60 AND summary.md exists → treat as valid M0. Document.
   If feeds < 60 → add P3 to recover. Document partial state.
4. **DB state**:
   ```sql
   SELECT COUNT(*) FROM data_flow.posts_features;
   SELECT COUNT(*) FROM data_flow.raw_content WHERE is_processed_by_rec=true;
   SELECT COUNT(*) FROM data_flow.articles;
   ```
   Record. Corpus snapshot baseline.
5. **Running processes**:
   ```bash
   ps aux | grep benchmark | grep -v grep
   docker ps --filter name=ml-rec-worker --format '{{.Status}}'
   ```
   If any benchmark script still running — wait for it OR kill it (depends on state).

**Deliverable**: `delivery_state.md` initial section documenting verified state + any issues found.

**Not a code phase — no commits expected.** Just documentation.

**Max turns**: 30 (verification only)

---

### Phase P1: pgvector search_path fix in main container (0.5 day)

**Goal**: propagate `search_path="data_flow,public"` fix from `scripts/benchmark.py` (commit `86b816c`) to `src/infrastructure/container.py` so every engine instance can resolve pgvector ops.

**Investigation first**:

1. Read `src/infrastructure/container.py` — find where SQLAlchemy engine is created.
2. Compare with `scripts/benchmark.py` — find the exact fix applied there.
3. Identify if main engine already has the fix or not.

**Implementation**:

- Add `connect_args={"server_settings": {"search_path": "data_flow,public"}}` to the main engine construction.
- Cover with a regression test: create a minimal test that exercises a pgvector operator via SQLAlchemy (e.g., `embedding <=> :vec`) using the main container's engine.
- Must not break existing tests.

**Acceptance**:
- [ ] Test proving pgvector op works via main engine (integration test with testcontainers PG + pgvector).
- [ ] `uv run pytest -m integration` still green.
- [ ] Diff limited to `container.py` + one new test file.

**TDD**:
- RED: write regression test reproducing the bug (fresh engine from container, SELECT with `<=>`). Must fail (or demonstrate absence of setting).
- GREEN: apply `connect_args`, test passes.

**Commits**:
- `test(infrastructure): RED regression test for pgvector search_path in main engine`
- `fix(infrastructure): propagate search_path=data_flow,public to main async engine`

**Max turns**: 50

---

### Phase P2: Investigation — Dedup Leak + Freshness 443h (1–1.5 days)

**Goal**: investigate and address TWO signals from M0 baseline:
- **Signal A**: dedup pair count > 0 in feeds (21 pairs in 15-item smoke)
- **Signal B**: `freshness_median = 443 hours`, vastly over `max_age = 168h` target

Both signals are proxy-metric alerts. Fix if bug, document if data characteristic.

### P2 part A — Dedup leak investigation

Agent writes findings to `.claude/artifacts/rec-eval/dedup_investigation.md`:

1. **Check dedup completion for telegram corpus**:
   ```sql
   SELECT COUNT(*) FROM data_flow.raw_content WHERE source_type='telegram' AND is_processed_by_dedup=false;
   SELECT COUNT(*) FROM data_flow.articles WHERE source='telegram' OR EXISTS (SELECT 1 FROM data_flow.raw_content r WHERE r.id = articles.raw_content_id AND r.source_type='telegram');
   ```
   If pending > 0 → dedup is behind, wait for drain. If articles count mismatches raw_content count → dedup is producing articles but silent failures somewhere.

2. **Check similarity graph coverage**:
   ```sql
   SELECT COUNT(*) FROM data_flow.similarities WHERE rel_type IN ('EXACT','DUPLICATE');
   SELECT rel_type, COUNT(*) FROM data_flow.similarities GROUP BY rel_type;
   ```
   If 0 EXACT/DUPLICATE edges for the new corpus — dedup ran but found no duplicates (suspicious for Telegram reposts).

3. **Sample a feed and inspect pairs**:
   - Pick 2 near-identical posts flagged as dedup pair by ProxyMetrics.
   - Check if their article rows are linked by EXACT or DUPLICATE in similarities table.
   - If YES → feed generation is not excluding them = retrieval-level bug.
   - If NO → dedup didn't catch them (threshold issue? text cleaning? embedding divergence?).

4. **Possible remediations** (choose based on root cause):

   **Case A — dedup didn't process**:
   - Wait for drain. No code change. Document timing.

   **Case B — dedup produces edges but feed doesn't filter them**:
   - Bug in `GenerateFeedUseCase.dedup_clustering_step`. Reproduce in test, fix, add regression test.

   **Case C — dedup threshold too loose**:
   - The cosine threshold for EXACT/DUPLICATE (managed by dedup-system, outside scope). Document and flag to dedup-system team.

   **Case D — cross-channel reposts are genuinely near-duplicates that dedup finds as RELATED but not EXACT/DUPLICATE**:
   - Expected behavior. RELATED posts are spaced via `dedup_params.related_min_gap`. If pair count still > 0, the spacing param may need tuning OR the threshold for what counts as "pair" in ProxyMetric is too strict.
   - Fix: document in `proxy_metrics.py` that `dedup_pair_count > 0` is expected in news domain with reposts; normalize metric interpretation.

**Deliverables**:
- `.claude/artifacts/rec-eval/dedup_investigation.md` — findings, root cause, action taken.
- If code fix: regression test + fix commit.
- If data characteristic: documentation update only, possibly adjust proxy_metric threshold or add a note.

**Dedup commits** (one of):
- `test(application): RED test for dedup leak in generate feed`, `fix(application): exclude EXACT/DUPLICATE from retrieval candidates`
- OR
- `docs(evaluation): document dedup_pair_count behavior in news-with-reposts domain`

### P2 part B — Freshness investigation (`freshness_median = 443h` vs 168h target)

Findings file: `.claude/artifacts/rec-eval/freshness_investigation.md`.

**Hypotheses to verify**:

1. **CSV ingestion set stale timestamps**: telegram_posts.csv has old `post_date` values. When ingested, `received_at` / `raw_data.publishedAt` reflect 2025 dates. But posts_features.processed_at should be "now" (when NLP ran). Check:
   ```sql
   SELECT 
     MIN(received_at), MAX(received_at),
     MIN(processed_at), MAX(processed_at),
     AVG(EXTRACT(EPOCH FROM (NOW() - processed_at)) / 3600) AS avg_age_hours
   FROM data_flow.posts_features pf
   JOIN data_flow.raw_content rc ON pf.post_id = rc.id
   WHERE rc.source_type = 'telegram';
   ```

2. **Retrieval max_age filter bypassed**: check `pg_content_repository.get_candidates_by_freshness` — what's the actual WHERE clause? Is it using `processed_at` or `received_at`?

3. **Freshness proxy metric uses wrong field**: check `ProxyMetricsComputer` — does it use `item.age_hours` derived from `received_at` (stale CSV dates) or `processed_at` (when NLP was run)?

**Likely root cause** (to validate): the proxy metric computes age from `received_at` or `post_date`, which is the ORIGINAL Telegram publish time (can be days or weeks ago from CSV). The retrieval filter uses `processed_at` (recent — when NLP processed the post), so it DOES return "fresh" posts in terms of processing, but their original publication is old.

If this is the case:
- **Option 1**: update ProxyMetrics to use `processed_at` for freshness — reflects "when this was available for serving"
- **Option 2**: keep `received_at` but document that CSV-ingested corpus has inherent 443h median because posts are from late 2025 imports
- **Option 3**: add filter `processed_at > NOW() - 30 days` to retrieval to align both signals

**Recommendation**: Option 1 (metric semantics), documented. Don't change retrieval — that's tuned for real-world flow where received_at ≈ processed_at.

**Freshness commits** (one of):
- `fix(evaluation): change freshness metric to use processed_at instead of received_at`
- OR
- `docs(evaluation): document freshness_median discrepancy due to CSV-ingested historical corpus`

**Combined P2 Acceptance**:
- Dedup pair count understood and addressed
- Freshness 443h understood and addressed
- Both investigation artifacts exist

**Max turns**: 100

---

### Phase P2b: LLM Judge Reliability (0.5 day)

**Goal**: eliminate LLM judge timeouts to restore 60/60 baseline integrity before any milestone benchmarks.

**Current state**: `tests/evaluation/llm_judge.py` uses `timeout_s = 120`. 3 of 60 combinations in M0 timed out. Lost judgments contaminate aggregate metrics.

**Fixes**:

1. **Bump default timeout**: 120 → 240 seconds. Add env var override `REC_LLM_JUDGE_TIMEOUT` (default 240).

2. **Add retry on timeout** (currently only retries on parse failure):
   ```python
   async def judge(self, persona, feed) -> JudgeResult:
       for attempt in range(max_retries):
           try:
               return await self._judge_once(persona, feed)
           except (TimeoutError, subprocess.TimeoutExpired) as e:
               if attempt == max_retries - 1:
                   raise LLMJudgeError(f"Timeout after {max_retries} attempts") from e
               logger.warning(f"LLM judge timeout, retry {attempt+1}/{max_retries}")
               continue
   ```
   `max_retries` default = 2. Add env `REC_LLM_JUDGE_MAX_RETRIES`.

3. **Reduce prompt size option**: add flag `--compact-snippets` to benchmark.py that truncates each post snippet to 100 chars instead of 200. Useful for personas with very long feeds.

**Tests**:
- Unit: timeout → retry → success (mocked subprocess raising TimeoutExpired first call, returning valid output second call).
- Unit: all retries time out → LLMJudgeError raised.
- Unit: env var overrides picked up correctly.

**Commits**:
- `test(evaluation): RED tests for LLMJudge timeout retry behavior`
- `fix(evaluation): add timeout retry and configurable REC_LLM_JUDGE_TIMEOUT / _MAX_RETRIES`
- `feat(scripts): add --compact-snippets flag to benchmark.py for prompt size reduction`

**Acceptance**:
- All new unit tests pass.
- Existing LLMJudge tests still green.
- Documented env vars in `.claude/artifacts/eval/README.md`.

**Max turns**: 60

---

### Phase P3: Restore 60/60 M0 baseline (0.5 day)

**Goal**: ensure M0 has a clean 60/60 judgments dataset after Phase P2b's LLM judge reliability fix.

**Starting state**: 60/60 feeds present, 57/60 judgments (3 timeouts).

**Steps**:

1. **Identify missing judgments**:
   ```bash
   cd /home/mattew/SKD/rec-system
   ls .claude/artifacts/eval/benchmarks/2026-04-18_initial_baseline/feeds/ | sed 's/.json$//' > /tmp/feeds.txt
   ls .claude/artifacts/eval/benchmarks/2026-04-18_initial_baseline/llm_judgments/ | sed 's/.json$//' > /tmp/judgments.txt
   diff /tmp/feeds.txt /tmp/judgments.txt  # shows what's missing
   ```
   Record the 3 missing (persona, run) pairs.

2. **Re-run judge for missing combinations** using a small helper script:
   ```python
   # scripts/re_judge_missing.py (temporary, can be reverted after use)
   # Loads the feed JSON for each missing combination, calls LLMJudge (now reliable post-P2b),
   # writes result to llm_judgments/{persona}_baseline_run{N}.json
   ```
   OR: extend `benchmark.py` with `--rejudge-only` flag that skips feed generation and only re-runs judge phase on existing feeds.

3. **Regenerate summary.md / metrics.csv** from the now-complete dataset:
   ```bash
   uv run python scripts/benchmark.py \
     --rebuild-summary \
     --output .claude/artifacts/eval/benchmarks/2026-04-18_initial_baseline/
   ```
   OR manually trigger the `BenchmarkRunner.finalize()` step.

4. **Canonicalize as M0**:
   ```bash
   ln -sfn 2026-04-18_initial_baseline milestone_m0_baseline
   ```
   (in `.claude/artifacts/eval/benchmarks/`)

**Acceptance**:
- 60/60 feeds + 60/60 judgments + `summary.md` + `metrics.csv` with 60 rows
- `milestone_m0_baseline` symlink exists
- No LLM judge timeouts on re-run (validates P2b fix)

**Commits**:
- `feat(evaluation): add --rejudge-only and --rebuild-summary flags to benchmark runner`
- OR helper script committed as `scripts/re_judge_missing.py` then removed after use (don't commit its removal — keep for future)

**Max turns**: 60

**This phase produces the canonical M0 milestone** for all subsequent comparisons.

---

### Phase P4: Merge feat/eval-harness → master (0.5 day)

**Goal**: merge the polished eval-harness branch into master. Feature phases A/B/C will branch off master.

**Steps**:

1. Verify `uv run pytest -m "unit or integration"` fully green on `feat/eval-harness` tip.
2. Ensure P1, P2 fixes are committed on `feat/eval-harness` (not orphaned).
3. Switch to master: `git checkout master && git pull`
4. Merge: `git merge feat/eval-harness --no-ff -m "feat(evaluation): eval harness + polish (Phase 0-8 + P1-P3)"`
5. Push: `git push origin master` (only if user confirmed push intent in top-level prompt — otherwise just local merge).
6. Run full test suite on master: `uv run pytest -m "unit or integration" --tb=short`
7. Checkout new branch: `git checkout -b feat/rec-improvements`

**Acceptance**:
- master has eval-harness work
- All tests green on master
- New branch `feat/rec-improvements` checked out
- `delivery_state.md` records merge commit hash

**Commits**:
- One merge commit on master (from step 4).

**Max turns**: 40

---

### Phase A: Live Profile Recomputation (2 days)

**Goal**: at `/recommendations` request time, blend the stored `rec_profiles.embedding` (EMA-based, updated every 5 min by background job) with a live-computed vector from the user's last N positive interactions. Result: faster cold-start, faster drift response.

### A.1. Design decisions

- **Which interactions contribute?** Only **positive signals** per CLAUDE.md signal weights table:
  - LIKE (+0.60)
  - BOOKMARK (+0.80)
  - CLOSE with scroll≥85% AND dur≥15s (+0.50)
  - IMPRESSION with dur≥2000ms (+0.15)
  - CLOSE with scroll≥50% AND dur≥10s (+0.40)
  - OPEN+CLOSE within 5min (+0.10)
  - NOT DISLIKE, NOT short-IMPRESSION-skip, NOT bounce-close (negative signals skip)
- **Blend formula**: `final_emb = normalize(α * profile.embedding + (1-α) * live_avg)` where `α` defaults to 0.6 and is configurable via `rec_config.live_profile_blend`.
- **Live avg**: weighted mean of post embeddings from recent positives, weighted by signal strength × age decay (exp(-age_hours/24)). Requires pre-computed post embeddings in `posts_features` — which we have.
- **How many recent events?** Default 15, configurable via `rec_config.live_profile_recent_n`.
- **Empty case**: if user has 0 positive interactions → skip live computation, use stored profile.embedding unchanged.
- **Cold-start case**: if profile.embedding is zero vector (cold) AND no positive interactions → still zero. Onboarding semantic expansion (NOT in this phase) would fix this — out of scope here.

### A.2. Code placement

```
src/application/services/live_profile_service.py       NEW
src/domain/interfaces/user_interaction_repository.py   Might need new method `get_recent_positive(user_id, limit)` — check existing interface
src/infrastructure/persistence/pg_interaction_repository.py   Impl of new method if added
src/application/use_cases/generate_feed.py             Modify — call LiveProfileService when flag enabled
src/infrastructure/container.py                        Wire LiveProfileService
```

### A.3. Contract

```python
# src/domain/interfaces/live_profile_computer.py (NEW abstract port if not exists)
class LiveProfileComputer(ABC):
    @abstractmethod
    async def compute(
        self,
        user_id: UUID,
        stored_profile: UserProfile,
        blend_alpha: float,
        recent_n: int,
    ) -> list[float] | None:
        """
        Return blended embedding or None if no live signal available.
        Length must match stored_profile.embedding (312).
        """
        ...
```

```python
# src/application/services/live_profile_service.py
class LiveProfileService(LiveProfileComputer):
    def __init__(
        self,
        interaction_repo: UserInteractionRepository,
        content_repo: ContentRepository,  # has get_by_ids() for post embeddings
        signal_classifier: SignalClassifier,  # to re-classify event strength
    ):
        ...

    async def compute(self, user_id, stored_profile, blend_alpha, recent_n):
        events = await self.interaction_repo.get_recent_positive(user_id, limit=recent_n)
        if not events:
            return None  # signal to caller: use stored profile

        signals = self.signal_classifier.classify_batch(events)
        positives = [s for s in signals if s.weight > 0]
        if not positives:
            return None

        post_ids = [s.content_id for s in positives]
        features = await self.content_repo.get_by_ids(post_ids)
        weights_and_embs = [
            (s.weight * exp_decay(s.created_at), f.embedding)
            for s, f in zip(positives, features)
            if f.embedding is not None
        ]
        if not weights_and_embs:
            return None

        live_avg = weighted_average_and_normalize(weights_and_embs)
        if stored_profile.embedding is None or all(v == 0 for v in stored_profile.embedding):
            return live_avg

        blended = blend_alpha * stored_profile.embedding + (1 - blend_alpha) * live_avg
        return normalize_l2(blended)
```

### A.4. Integration into generate_feed

In `GenerateFeedUseCase.execute`:

```python
# Existing
profile = await profile_repo.get(user_id)

# NEW — feature-flagged
if os.environ.get("REC_FEATURE_LIVE_PROFILE", "false") == "true":
    blend = float(rec_config.get("live_profile_blend", 0.6))
    recent_n = int(rec_config.get("live_profile_recent_n", 15))
    live_emb = await self._live_profile.compute(user_id, profile, blend, recent_n)
    if live_emb is not None:
        profile = profile.with_embedding(live_emb)  # immutable copy
```

The rest of the pipeline (retrieval, scoring, etc.) uses `profile.embedding` as before — no further changes.

### A.5. Config

Add to `data_flow.rec_config` seed (migration or config loader):
- `live_profile_blend = 0.6`
- `live_profile_recent_n = 15`
- `live_profile_decay_hours = 24`

### A.6. Tests

Unit (`tests/unit/application/services/test_live_profile_service.py`):
- Zero events → returns None
- Only negative signals → returns None
- Events with no embeddings → returns None
- Single positive event, cold stored profile → returns normalized live vector
- Multiple positives → weighted average correct to numerical precision
- Blend α=0.0 → returns live only
- Blend α=1.0 → returns stored only
- Age decay applied correctly

Integration (`tests/integration/application/use_cases/test_generate_feed_live_profile.py`):
- With flag off: identical feed to baseline (byte-equal ranking)
- With flag on, zero events: identical feed to baseline
- With flag on, 3 LIKE on tech posts: feed shifts toward tech
- With flag on, persona drift scenario: feed reflects recent interests faster than EMA

### A.7. Feature flag acceptance

- `REC_FEATURE_LIVE_PROFILE=false` (default): zero behavior change vs master.
- `REC_FEATURE_LIVE_PROFILE=true`: feed uses live-blended profile.
- Test both flag states in CI.

### A.8. Commits

- `test(application): RED unit tests for LiveProfileService`
- `feat(application): implement LiveProfileService with weighted decay and blend`
- `test(application): RED integration tests for generate_feed with live profile flag`
- `feat(application): integrate LiveProfileService into GenerateFeedUseCase behind REC_FEATURE_LIVE_PROFILE flag`
- `feat(config): seed live_profile_* defaults in rec_config`

**Max turns**: 100

**After completion**: orchestrator runs M1 benchmark.

---

### M1 Milestone Benchmark (orchestrator responsibility, not a sub-claude phase)

Orchestrator runs:

```bash
cd /home/mattew/SKD/rec-system
REC_FEATURE_LIVE_PROFILE=true \
REC_FEATURE_HOT_ARRIVAL=false \
REC_FEATURE_RERANK=false \
  uv run python scripts/benchmark.py \
    --personas-all --versions baseline \
    --runs 3 --count 30 --seed 42 \
    --enable-llm-judge --judge-model haiku \
    --output .claude/artifacts/eval/benchmarks/milestone_m1_phase_a/ \
    --tag m1_phase_a \
    --db-url <live>
```

**Expected cost**: ~$4 (same as M0, since it's identical persona/run count).

**Acceptance**: 60/60 feeds, summary.md, no errors.

**If M1 LLM mean < M0 LLM mean by > 5%**: Phase A regressed. STOP. Investigate before proceeding. Potential causes: overfitting to noisy recent events, blend param mis-tuned, sigal classification bug.

**If M1 LLM mean >= M0**: proceed to Phase B.

**Orchestrator writes**: `.claude/artifacts/rec-eval/comparisons/m0_vs_m1.md` — table of deltas per persona per metric.

---

### Phase B: On-Arrival Hot-Content Encoding (1–2 days)

**Goal**: when a new post arrives (via parser's `content.published` Kafka topic OR fresh raw_content row), encode it IMMEDIATELY instead of waiting up to 30 seconds for `content_processing` scheduler. Reduces breaking-news latency from ~30s to ~1s.

### B.1. Design decisions

- **Trigger source**: two options.
  - **Option 1 (Kafka)**: subscribe to existing `content.published` topic with consumer group `rec-system-hot-content`.
  - **Option 2 (DB polling with shorter interval)**: add a "fast" scheduler job running every 2s that only processes posts marked `priority='breaking'` or `hot=true`.
  - **Decision**: use Option 1. Rationale: Kafka already defines the event, avoids duplicate polling load on DB. If topic is not published by parser (uncertain state) → fallback: no-op consumer, Phase B silently does nothing. Acceptable degradation.
- **What gets encoded immediately?** Only posts meeting "hot" criteria to avoid overloading:
  - `raw_data.priority == 'breaking'` (if parser sets it), OR
  - `source_id` belongs to curated "breaking news" source list (configurable), OR
  - Default if no signal: always encode immediately (lightweight enough).
- **Idempotency**: the background `content_processing` job may process the same post moments later. Use `INSERT ... ON CONFLICT (post_id) DO NOTHING` so hot-arrival wins, background job gracefully skips.
- **Feature flag**: `REC_FEATURE_HOT_ARRIVAL=true`. Default off. If off, consumer doesn't start.
- **Observable**: log `hot_content_processed` with post_id and latency for every post.

### B.2. Code placement

```
src/application/services/hot_content_encoder.py       NEW — processes a single post
src/presentation/consumers/content_published_consumer.py   NEW — Kafka consumer
src/application/dto/content_published_event.py        NEW — Kafka message schema (from parser's topic)
src/main.py                                           Modify — start consumer in lifespan if flag enabled
```

### B.3. Kafka message schema (assumed, verify with parser-service docs)

```json
{
  "event_id": "uuid",
  "occurred_at": "ISO-8601",
  "content_id": "uuid",  // raw_content.id
  "source_id": "uuid",
  "source_type": "telegram|rss|...",
  "priority": "normal|breaking",
  "clean_text_length": 520
}
```

If actual schema differs — adapt. If Kafka topic doesn't exist at runtime — consumer fails to connect, log warning, treat as no-op.

### B.4. Consumer lifecycle

```python
# src/presentation/consumers/content_published_consumer.py
class ContentPublishedConsumer:
    def __init__(self, encoder: HotContentEncoder, hot_enabled: bool):
        self._encoder = encoder
        self._enabled = hot_enabled
        self._running = False

    async def start(self):
        if not self._enabled:
            logger.info("content_published_consumer_disabled")
            return
        # Connect to Kafka, subscribe to content.published, group=rec-system-hot-content
        # Loop: on each message → self._encoder.process(content_id)
        # On shutdown: gracefully close

    async def stop(self): ...
```

### B.5. Encoder service

```python
class HotContentEncoder:
    def __init__(self, content_repo, topic_classifier, sentiment_analyzer,
                 entity_extractor, content_encoder, text_analyzer):
        ...

    async def process(self, content_id: UUID) -> None:
        # 1. Load raw_content row
        # 2. Run NLP pipeline (same as ProcessContentUseCase.execute_one)
        # 3. UPSERT posts_features with ON CONFLICT DO NOTHING
        # 4. Mark is_processed_by_rec=true in raw_content
        # 5. Log hot_content_processed with latency
```

**Reuse `ProcessContentUseCase._process_single`** if possible. Extract shared logic into a private helper if it's not already reusable. Don't duplicate NLP orchestration.

### B.6. Lifespan integration

```python
# src/main.py
@asynccontextmanager
async def lifespan(app: FastAPI):
    # existing startup
    if os.environ.get("REC_FEATURE_HOT_ARRIVAL", "false") == "true":
        consumer_task = asyncio.create_task(consumer.start())
    yield
    # existing shutdown
    if os.environ.get("REC_FEATURE_HOT_ARRIVAL", "false") == "true":
        await consumer.stop()
```

### B.7. Tests

Unit:
- `HotContentEncoder.process` with mocked repos — NLP pipeline invoked correctly, UPSERT called, flag set.
- Consumer loop with mocked aiokafka consumer — message parse, dispatch, error handling (malformed message, unknown content_id, NLP failure).

Integration (testcontainers Kafka + PG):
- Publish synthetic `content.published` event → assert posts_features row created within 2s.
- Race with background `content_processing` — both run against same post, one wins, idempotency holds.

### B.8. Risks

- **If `content.published` topic is not actually produced by parser**: Phase B does nothing, but doesn't harm anything. Investigate and coordinate with parser-service team. Document in findings.
- **NLP on request path**: even if "hot" path is small, running NLP pipeline blocks one event loop slot for ~150ms. Keep it async via `asyncio.to_thread` (same as content_processing currently does).
- **Kafka rebalance storms**: if many replicas join/leave consumer group, duplicated processing possible. `ON CONFLICT DO NOTHING` handles it.

### B.9. Feature flag acceptance

- `REC_FEATURE_HOT_ARRIVAL=false` (default): consumer doesn't start, zero background activity added.
- `REC_FEATURE_HOT_ARRIVAL=true`: consumer starts, processes messages.

### B.10. Commits

- `test(application): RED unit tests for HotContentEncoder`
- `feat(application): implement HotContentEncoder reusing ProcessContentUseCase NLP logic`
- `test(presentation): RED tests for ContentPublishedConsumer`
- `feat(presentation): implement Kafka consumer for hot-content path`
- `feat(main): wire hot content consumer into FastAPI lifespan behind REC_FEATURE_HOT_ARRIVAL`

**Max turns**: 120

### M2 Milestone Benchmark (orchestrator)

Same benchmark invocation as M1 but with BOTH flags on:
```bash
REC_FEATURE_LIVE_PROFILE=true REC_FEATURE_HOT_ARRIVAL=true REC_FEATURE_RERANK=false \
  uv run python scripts/benchmark.py ... --tag m2_phase_a_b ...
```

**Expectation**: M2 should be **similar to M1** on proxy metrics and LLM score. Phase B affects freshness (new posts appear faster), not relevance per se. Delta may be near-zero in offline benchmark without time-sensitive personas. Document this as expected.

**If M2 regresses vs M1 by >5%**: investigate. Possible: hot-arrival race condition corrupting embeddings, Kafka consumer leaking resources.

Orchestrator writes: `.claude/artifacts/rec-eval/comparisons/m1_vs_m2.md`.

---

### Phase C: Cross-Encoder Reranking (4–5 days)

**Goal**: add a cross-encoder reranking stage that scores (user_context, post_text) pairs and reranks top-N candidates before diversity filtering. This is THE relevance improvement.

### C.1. Model selection

**Recommendation**: `BAAI/bge-reranker-v2-m3` (multilingual, proven on Russian, ~568MB bf16, native fp16 support on our GPU).

**Alternatives** (if above issues):
- `jinaai/jina-reranker-v2-base-multilingual` (~270MB, faster)
- `DeepPavlov/rubert-base-cased` used as cross-encoder (needs wrapping, less optimal)

**Must**: be multilingual or Russian-native. Do NOT use English-only `cross-encoder/ms-marco-*` models — they perform poorly on Russian.

**Do not fine-tune**. Use pretrained weights. Distillation/fine-tuning is out of scope (multi-week effort).

### C.2. User context construction

Cross-encoder needs a textual "query" representing the user. Build from profile state:

```python
class UserContextBuilder:
    def build(self, profile: UserProfile, recent_events: list[UserInteraction]) -> str:
        parts = []

        # 1. Top-3 topics from profile topic_vector
        top_topics = sorted(profile.topic_vector.items(), key=lambda x: -x[1])[:3]
        parts.append(f"Темы: {', '.join(t for t,_ in top_topics)}")

        # 2. Top-5 entity interests
        top_entities = await entity_interests_repo.get_top_n(profile.user_id, 5)
        if top_entities:
            parts.append(f"Сущности: {', '.join(e.entity_name for e in top_entities)}")

        # 3. Last 3 positive interaction titles (if any)
        if recent_events:
            titles = [f"«{e.content_title}»" for e in recent_events[:3] if e.content_title]
            if titles:
                parts.append(f"Недавно понравилось: {' '.join(titles)}")

        return ". ".join(parts)
```

Example output:
```
Темы: технологии, наука, бизнес. Сущности: OpenAI, Илон Маск, SpaceX. Недавно понравилось: «GPT-5 released» «Tesla Q3 earnings» «SpaceX launches Starship»
```

### C.3. Contract

```python
# src/domain/interfaces/relevance_reranker.py
class RelevanceReranker(ABC):
    @abstractmethod
    async def rerank(
        self,
        user_context: str,
        candidates: list[ContentFeatures],  # each has title + content snippet
        top_k: int,
    ) -> list[tuple[UUID, float]]:
        """Return [(post_id, rerank_score)] sorted by score desc, length=min(top_k, len(candidates))."""
        ...
```

### C.4. Adapter

```python
# src/infrastructure/nlp/torch_cross_encoder.py
class TorchCrossEncoder(RelevanceReranker):
    def __init__(
        self,
        model_name: str = "BAAI/bge-reranker-v2-m3",
        device: str = "cuda",
        batch_size: int = 32,
        max_length: int = 512,
    ):
        self._model = FlagReranker(model_name, use_fp16=True)  # BGE reranker has native API
        self._batch_size = batch_size
        self._max_length = max_length

    async def rerank(self, user_context, candidates, top_k):
        if not candidates:
            return []
        pairs = [(user_context, f"{c.title or ''} {c.content_snippet}") for c in candidates]
        scores = await asyncio.to_thread(self._rerank_sync, pairs)
        id_score = list(zip([c.post_id for c in candidates], scores))
        id_score.sort(key=lambda x: -x[1])
        return id_score[:top_k]

    @torch.no_grad()
    def _rerank_sync(self, pairs):
        return self._model.compute_score(pairs, batch_size=self._batch_size, max_length=self._max_length)
```

Install dependency: `uv add FlagEmbedding` (BGE reranker's official library) OR use raw `transformers.AutoModelForSequenceClassification` if `FlagEmbedding` adds too much bloat.

### C.5. Integration in GenerateFeedUseCase

```python
# After scoring, before diversity filter:
if os.environ.get("REC_FEATURE_RERANK", "false") == "true":
    top_n = int(rec_config.get("rerank_top_k", 100))
    blend = float(rec_config.get("rerank_weight", 0.5))

    pre_sorted = sorted(scored_candidates, key=lambda c: -c.scoring_score)[:top_n]
    user_ctx = await context_builder.build(profile, recent_events)
    rerank_scores_dict = dict(await reranker.rerank(user_ctx, pre_sorted, top_k=top_n))

    # Blend: final = (1-blend)*pre_sorted_score_normalized + blend*rerank_score_normalized
    for c in pre_sorted:
        if c.post_id in rerank_scores_dict:
            c.final_score = (1 - blend) * c.scoring_score_normalized + blend * rerank_scores_dict[c.post_id]

    # Items beyond top_n keep their original scoring_score as final_score
    final_sorted = sorted(all_candidates, key=lambda c: -c.final_score)
else:
    final_sorted = sorted(scored_candidates, key=lambda c: -c.scoring_score)

# Continue to diversity filter...
```

### C.6. Config

Add to `rec_config`:
- `rerank_top_k = 100`
- `rerank_weight = 0.5` (blend weight; 0=no rerank effect, 1=only rerank score)
- `rerank_min_candidates = 30` (skip rerank if fewer candidates than this)

### C.7. Tests

Unit:
- `TorchCrossEncoder` with mocked model — verify batch construction, score ordering, empty input.
- `UserContextBuilder` deterministic construction from profile.
- Integration in `generate_feed`: with flag off, identical to M2; with flag on, reranker invoked, scores blended.

Integration:
- End-to-end generate_feed with real BGE reranker (loaded once) — verify feed order changes vs flag-off, latency measured.

Performance:
- Benchmark: single feed request with flag on should finish < 1s on RTX 5060 Ti with top_k=100.

### C.8. VRAM budget

Before Phase C: rec-worker uses ~1.1 GB.
After: +BGE reranker weights 568 MB + batch-32 activations ~200 MB = rec-worker ~2 GB steady.
Total (with dedup still sharing GPU): ~5 GB used of 16 GB. Safe.

If codebase-rag is still eating 5.6 GB → tight but workable. Flag to user if OOM risk observed.

### C.9. Risks

- **Pretrained model may not improve quality**: possible outcome is M3 ≈ M2. That's a data point worth knowing. If true, consider fine-tune in future iteration.
- **Latency**: 50 pairs × ~10ms/pair on GPU = 500ms added. p95 feed latency jumps from ~200ms to ~700ms. Documented trade-off.
- **VRAM contention with dedup**: if dedup is running a big forward pass when a feed request arrives, potential OOM. Mitigation: torch.cuda.empty_cache() periodically. If real issue — consider BGE-small variant.

### C.10. Feature flag acceptance

- `REC_FEATURE_RERANK=false` (default): no model loaded, no latency added.
- `REC_FEATURE_RERANK=true`: model loaded at startup singleton, invoked on every feed request.

### C.11. Commits

- `test(infrastructure): RED tests for TorchCrossEncoder with mocked model`
- `feat(infrastructure): implement TorchCrossEncoder adapter with BGE-reranker-v2-m3`
- `test(application): RED tests for UserContextBuilder`
- `feat(application): implement UserContextBuilder from profile + recent interactions`
- `test(application): RED integration tests for generate_feed with rerank flag`
- `feat(application): integrate reranking into GenerateFeedUseCase behind REC_FEATURE_RERANK flag`
- `feat(config): seed rerank_* defaults in rec_config`
- `chore(deps): add FlagEmbedding for BGE reranker`
- `chore(infra): update docker-compose with REC_FEATURE_RERANK env`

**Max turns**: 180

### M3 Milestone Benchmark (orchestrator)

All three flags on:
```bash
REC_FEATURE_LIVE_PROFILE=true REC_FEATURE_HOT_ARRIVAL=true REC_FEATURE_RERANK=true \
  uv run python scripts/benchmark.py ... --versions rerank --tag m3_phase_a_b_c ...
```

**Expectation**: M3 LLM mean > M2 by >5%. If not, Phase C did not deliver. Document and flag for future iteration.

Orchestrator writes: `.claude/artifacts/rec-eval/comparisons/m2_vs_m3.md` + `m0_vs_m3.md` (end-to-end delta).

---

### Final Phase: Comparison and Report (orchestrator, 0.5 day)

**Goal**: synthesize all milestones into a single deliverable.

**Tool**: `scripts/compare_benchmarks.py` (implement as part of final phase, or as standalone orchestrator task).

```bash
uv run python scripts/compare_benchmarks.py \
    --baseline milestone_m0_baseline \
    --candidate milestone_m1_phase_a \
    --output comparisons/m0_vs_m1 \
    --llm-judge-stats  # include Wilcoxon signed-rank on per-persona LLM scores
```

**Outputs per comparison**:
- `summary.md` — table of per-metric deltas per persona, color-coded winners/losers
- `diff.csv` — flat table for ad-hoc analysis
- `winners_losers.md` — narrative: which personas gained the most, which lost

**Final deliverable**: `/home/mattew/SKD/.claude/artifacts/rec-eval/delivery_final_report.md`:

```markdown
# Rec-System Feature Delivery — Final Report

## Summary

M0 baseline LLM mean: 3.12
M1 (+A) LLM mean: 3.41 (+9.3%, significant p=0.02)
M2 (+A+B) LLM mean: 3.43 (+0.6% vs M1, not significant)
M3 (+A+B+C) LLM mean: 3.89 (+13.4% vs M2, significant p=0.001)

Total delta M0→M3: +24.7% on LLM mean relevance.

## Per-phase analysis
...

## Per-persona winners
tech-geek: 3.3 → 4.1 (+24%)
drifting-tech-to-politics: 2.8 → 3.9 (+39%) — biggest win, live profile really helps
cold-start-empty: 2.1 → 3.4 (+62%) — live profile carries cold-start

## Per-persona losers
sports-fan: 3.5 → 3.3 (-6%) — rerank model weak on sports niche

## Latency
p95: 180ms → 650ms
p99: 280ms → 920ms

## VRAM
Peak: 1.1 GB → 2.3 GB (within budget)

## Recommendations
- Merge feat/rec-improvements
- Default all flags OFF in production until live user data confirms
- Enable REC_FEATURE_LIVE_PROFILE=true for safety
- Enable REC_FEATURE_RERANK=true via canary rollout
- Investigate sports-fan regression before wider rollout
```

**Max turns (for comparison script + final report)**: 80

---

## 6. Artifacts Structure

```
/home/mattew/SKD/.claude/artifacts/rec-eval/
├── orchestration_state.md            # from prior project, append-only
├── final_report.md                   # from prior project
├── phase{0..8}_prompt.txt            # from prior project
├── delivery_state.md                 # NEW — this project's tracker
├── delivery_prompt_{P0,P1,P2,P3,P4,A,B,C,Final}.txt    # NEW — prompts sent
├── dedup_investigation.md            # NEW — P2 findings
├── delivery_final_report.md          # NEW — final synthesis
└── comparisons/
    ├── m0_vs_m1/summary.md
    ├── m1_vs_m2/summary.md
    ├── m2_vs_m3/summary.md
    └── m0_vs_m3/summary.md           # end-to-end

/home/mattew/SKD/rec-system/.claude/artifacts/eval/benchmarks/
├── 2026-04-18_initial_baseline/       # M0 — from prior project
├── milestone_m0_baseline/             # symlink or copy of above, canonical name
├── milestone_m1_phase_a/              # NEW after Phase A
├── milestone_m2_phase_a_b/            # NEW after Phase B
└── milestone_m3_phase_a_b_c/          # NEW after Phase C
```

---

## 7. Testing Strategy

Same markers as prior project: `unit`, `integration`, `llm`, `slow`.

Each feature phase (A, B, C) must:
- Have `@pytest.mark.unit` coverage ≥ 90% on new code
- Have at least one `@pytest.mark.integration` test end-to-end with testcontainers
- Feature flag off case: verify byte-identical output to master (bit-exact regression guard)
- Feature flag on case: verify expected behavior change

**Mandatory CI gate before milestone benchmark**:
```bash
uv run pytest -m "unit or (integration and not slow)" --tb=short
```
Must be all green before orchestrator runs benchmark.

---

## 8. Launch command (for user to paste to SKD orchestrator)

```bash
cd /home/mattew/SKD && claude
```

Inside session:

```
Запускаем проект "Rec-System Feature Delivery (Phase A/B/C + Polish)".

Спека: /home/mattew/SKD/rec-system-feature-delivery-spec.md
Companion: /home/mattew/SKD/rec-system-eval-harness-spec.md (предыдущий проект, завершён на ветке feat/eval-harness)

Прочти ОБЕ спеки целиком, особое внимание Section 4 Orchestration Model в новой и Section 5 в предыдущей (шаблон per-phase делегации идентичен).

Протокол:
1. Создай tracker в /home/mattew/SKD/.claude/artifacts/rec-eval/delivery_state.md
2. Проверь, завершился ли initial_baseline benchmark (может всё ещё работать в фоне при старте)
3. Выполняй фазы в порядке: P0 → P1 → P2 → P3 (conditional) → M0 verify → P4 (merge) → A → M1 bench → B → M2 bench → C → M3 bench → Final compare
4. После каждой phase: verify per §4.5, update delivery_state.md, commit tracker
5. После каждого M1/M2/M3 бенчмарка: сравни с предыдущим, напиши comparisons/m{N-1}_vs_m{N}.md, покажи мне дельту
6. Останавливайся на: BLOCKED / FAILED / M-регрессии >5% на LLM mean / приближение к $150

Выполняй автономно до завершения (per autonomous_to_done memory). 
Я буду смотреть tracker + comparison отчёты между фазами и могу интервениться.

После Final: жди подтверждения от меня на merge feat/rec-improvements → master.
```

---

## 9. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| Phase C delivers no improvement (pretrained model mediocre) | Medium | Wasted 5 days | M3 bench honest, stop; document; don't merge Phase C |
| VRAM OOM during M3 benchmark with all three on | Low | Bench fails | Monitor nvidia-smi during bench; fall back to smaller BGE variant |
| pgvector fix (P1) breaks existing tests | Low | Blocks merge | TDD regression test; if tests break, rollback and investigate search_path conflicts |
| Dedup pair count stays > 0 after P2 | Medium | Cosmetic concern only | Document as known characteristic; don't block release |
| Phase B Kafka topic doesn't exist | High | Phase B no-ops | Consumer gracefully logs, no harm |
| Phase A worsens "stable-interest" personas (overfit to noise) | Medium | Regression in M1 | Only blend positive signals; A/B by persona in M1 report; tune blend alpha |
| Initial baseline benchmark incomplete at project start | High | Blocks M0 | P3 handles it; or use partial 14/60 result with documented limitation |
| Cost exceeds $150 | Low | Orchestrator stops and asks | Per-phase $20 cap + total $150 cap; orchestrator alerts at 80% |
| Sub-claude edits files outside scope | Medium | Scope creep, bad commits | Explicit "do not touch X" lists in prompt; orchestrator reviews diff before next phase |
| Merge conflict P4 if master advanced during harness work | Low | Manual resolution needed | Check `git log master..feat/eval-harness` and `git log feat/eval-harness..master`; resolve before merge |

---

## 10. References

- Prior project spec: `/home/mattew/SKD/rec-system-eval-harness-spec.md`
- Prior project artifacts: `/home/mattew/SKD/.claude/artifacts/rec-eval/`
- rec-system CLAUDE.md: `/home/mattew/SKD/rec-system/CLAUDE.md`
- BGE reranker: https://huggingface.co/BAAI/bge-reranker-v2-m3
- Cross-encoder concept: https://www.sbert.net/examples/applications/cross-encoder/README.html

---

## End of specification

**SKD orchestrator**: your first action is to read §4 Orchestration Model and §5 Phases end-to-end, then:

1. Verify current state per Phase P0.
2. Create `/home/mattew/SKD/.claude/artifacts/rec-eval/delivery_state.md` tracker.
3. Execute phases in order per dependency graph.
4. Run M0 bench (or use existing `initial_baseline` if valid), M1, M2, M3.
5. Produce `delivery_final_report.md`.
6. Wait for user to authorize `feat/rec-improvements` → master merge.

Feature flags are the core design principle: each phase must be independently togglable. Default OFF. Zero production behavior change until flag set.

Build quality. Measure honestly. Report everything.
