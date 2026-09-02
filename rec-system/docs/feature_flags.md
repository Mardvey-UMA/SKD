# rec-system Feature Flags — Production Policy

**Status**: authoritative policy as of 2026-04-19 (post Feature Delivery Phase A/B/C merge).
**Audience**: operators, release engineers, ML reviewers.

All three flags default **OFF** in code and are NOT to be toggled via `docker-compose.yml` or any k8s manifest. Enable only per this policy, via explicit config change reviewed by the rec-system owner.

---

## `REC_FEATURE_LIVE_PROFILE`

**Status**: default **OFF**. NOT recommended for broad enable without tuning.

**What it does**: at `/recommendations` request time, blends the stored EMA profile embedding with a live-recomputed vector from the user's recent positive interactions (default blend α=0.6, recent_n=15, decay_hours=24).

**Offline benchmark result** (M0→M1_FC, fixed corpus): aggregate llm_mean **-0.76%** (p=0.52, not significant). Per-persona mixed; niche personas tend to gain, well-profiled personas tend to drift.

**Why the offline null is expected**: Phase A addresses production-time phenomena (cold-start, drift response) that a deterministic `BehaviorSimulator` on a static corpus cannot exercise.

**Enable criteria**:
- Next iteration lowers `blend` alpha (try 0.3–0.4 to keep stored EMA dominant).
- Add a "skip live recomputation when stored profile age < X hours and has ≥ N events" gate to protect stable profiles.
- Canary with A/B test: 5% of users, measure dwell time and interaction depth vs control for 2 weeks.

**Do not enable** in production until the tuning iteration above ships.

---

## `REC_FEATURE_HOT_ARRIVAL`

**Status**: default **OFF**. Enable gated on producer readiness and arrival rate.

**What it does**: subscribes to Kafka topic `content.published`, encodes arriving posts through the NLP pipeline immediately (UPSERT `posts_features`, `raw_content.is_processed_by_rec=true`), bypassing the 30-s `content_processing` scheduler.

**Offline benchmark result**: by design, offline no-op (Kafka consumer activates only in FastAPI lifespan; benchmark bypasses).

**Enable criteria**:
- parser-service confirms it is actively publishing to `content.published` with schema `{content_id: UUID, ...}` and `priority` field set for at least breaking-news items.
- Measured arrival rate > 100 posts/min sustained over 24 h (below this, scheduler latency is already acceptable).
- OR parser-service explicitly emits `priority='breaking'` for time-sensitive posts; then enable regardless of rate to cut breaking-news latency from 30 s → ~1 s.

**Safety**: consumer idles gracefully if topic is empty or unreachable. No data corruption risk. Conservative rollout acceptable.

---

## `REC_FEATURE_RERANK`

**Status**: default **OFF**. **NOT recommended in current form.**

**What it does**: second-stage reranking of top-100 scored candidates using pretrained `BAAI/bge-reranker-v2-m3` cross-encoder. Blends rerank score with original scoring-formula score at weight 0.5.

**Offline benchmark result** (M2→M3_FC): aggregate llm_mean **+1.42%** (p=0.87 not significant), but **bimodal per-persona**:
- Winners: entity-focused-musk **+666.7%** (NER-blocked persona rescued by semantic matching), military-watcher +20.8%, entity-focused-putin +14.9%, long-form-reader +11.6%, entertainment +10.6%.
- Losers: science-only -17.7%, political-junkie -11.6%, sports-fan -11.0%, skimmer -10.5%, breaking-news-chaser -7.4%.

**Interpretation**: the pretrained multilingual reranker delivers differentiated rather than uniform lift. It **helps** personas whose relevance is blocked by NER string-matching or who live in narrow niches; it **hurts** mainstream-news-heavy personas where the 6-component scoring formula already captures relevance well and BGE's generic signal adds noise.

**Rollout blocked on**: a separate ML initiative to **fine-tune the reranker on rec-system domain data** (user click/like signals) before production enable. Without domain fine-tuning, enabling globally would regress the mainstream persona majority.

**Interim options (not recommended without ML review)**:
- Per-persona gate: enable only for profiles whose top-entity-interests include low-frequency entities likely missed by NER.
- Hybrid: apply rerank only when the scoring-formula top-K score variance is low (i.e. model is uncertain).

**Do not enable** via config alone. Requires separate project: "Fine-tune BGE reranker on rec-system interaction data".

---

## Invariants all three flags must respect

1. **Default OFF in code.** Changing default requires explicit code review by rec-system owner.
2. **NEVER set in shared infra files** (`docker-compose.yml`, any k8s manifest). Enable only via per-environment overlay (e.g. one-off env export in a canary deployment).
3. **Byte-equal flag-off regression** is enforced by integration tests. Breaking it is a release blocker.
4. **Independent toggleability**. Each flag controls its own feature; interaction effects (A+C especially) require explicit re-measurement, not inference.

---

## References

- Project spec: `/home/mattew/SKD/rec-system-feature-delivery-spec.md`
- Final report: `/home/mattew/SKD/artifacts/rec-eval/delivery_final_report.md`
- Comparison matrix: `/home/mattew/SKD/artifacts/rec-eval/comparisons/m0_vs_m3.md`
- Alembic seeds: `013_seed_live_profile_params.py`, `014_seed_rerank_params.py`
