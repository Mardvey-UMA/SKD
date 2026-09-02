# Rec-System Evaluation Harness

End-to-end evaluation pipeline: persona simulation → feed generation → proxy metrics → LLM judge → benchmark artifacts.

## Quick start

```bash
uv run python scripts/benchmark.py \
    --personas-all \
    --versions baseline \
    --runs 3 --count 30 --seed 42 \
    --enable-llm-judge \
    --output artifacts/my_run/
```

## Directory layout

```
tests/evaluation/
├── models.py              # Persona, InteractionPattern dataclasses
├── persona_loader.py      # Load persona YAML files from personas/
├── behavior_simulator.py  # Simulate user behaviour, onboard + interact
├── feed_evaluator.py      # Call GenerateFeedUseCase, build FeedResult
├── proxy_metrics.py       # 8 objective metrics (entropy, freshness, …)
├── llm_judge.py           # LLM-as-judge (Claude CLI subprocess)
├── benchmark_runner.py    # Orchestrate N×M×K runs, persist artifacts
├── personas/              # YAML persona definitions
├── prompts/               # feed_judge.txt — LLM judge prompt template
├── templates/             # summary.md.j2 — Jinja2 benchmark summary
└── versions.yaml          # Named version configs (env overrides + rec_config)
```

## LLM Judge (`llm_judge.py`)

Calls the Claude CLI (`claude -p`) via subprocess to score each feed item's relevance to a persona (0-5 scale).

### Configuration

| Env var | Default | Description |
|---------|---------|-------------|
| `REC_LLM_JUDGE_TIMEOUT` | `240` | Subprocess timeout in seconds per Claude call |
| `REC_LLM_JUDGE_MAX_RETRIES` | `1` | Max retry attempts (shared between timeout and parse-failure retries) |

Both env vars can be overridden by passing explicit kwargs to `LLMJudge()`:

```python
judge = LLMJudge(timeout_s=300, max_retries=2)
```

### Retry policy

- **Timeout**: `subprocess.TimeoutExpired` triggers a retry with exponential backoff (5s → 10s → 20s cap). All retries exhausted → `LLMJudgeError` raised.
- **Parse failure**: JSON parse failure on ≥20% of items triggers a retry with a corrective prompt. No backoff (immediate retry).
- Both failure types share the `max_retries` counter.

### Snippet length

By default each post's `content_snippet` is truncated to **200 chars** in the judge prompt. Pass `snippet_max_len=100` for shorter prompts.

## Benchmark CLI (`scripts/benchmark.py`)

```
uv run python scripts/benchmark.py [options]
```

### Key flags

| Flag | Default | Description |
|------|---------|-------------|
| `--personas IDS` / `--personas-all` | — | Which personas to evaluate |
| `--versions VERSIONS` | `baseline` | Comma-separated version names from `versions.yaml` |
| `--runs N` | `1` | Runs per (persona × version) combination |
| `--count N` | `30` | Feed size per run |
| `--seed N` | `42` | Base random seed (incremented per run) |
| `--enable-llm-judge` | off | Enable LLM judge (costs money) |
| `--judge-model MODEL` | `haiku` | Claude model for judging |
| `--compact-snippets` | off | Truncate judge prompt snippets to 100 chars (vs 200). Reduces latency and token cost. |
| `--skip-existing` | off | Skip combinations with existing feed JSON |
| `--output DIR` | auto | Output directory for all artifacts |
| `--tag TAG` | `benchmark` | Short label in output dir name and summary |
| `--db-url URL` | `$DATABASE_URL` | PostgreSQL async connection URL |

## Artifacts

Each run produces under `--output`:

```
feeds/{persona}_{version}_run{N}.json
llm_judgments/{persona}_{version}_run{N}.json
metrics.csv
costs.csv
summary.md
personas_snapshot.yaml
config_snapshot.yaml
versions_used.yaml
errors.log
```

## Running tests

```bash
# Unit tests only (fast, no DB)
uv run pytest tests/evaluation -m unit -q

# Integration tests (needs Docker for testcontainers)
uv run pytest tests/evaluation -m integration -q

# LLM tests (calls real Claude CLI, costs money)
uv run pytest tests/evaluation -m llm -q
```
