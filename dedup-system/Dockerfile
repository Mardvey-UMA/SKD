# ─────────────────────────────────────────────────────────────────────────────
#  Stage 1: deps
#  Устанавливает все Python-зависимости из lockfile.
#  Кэшируется до изменения pyproject.toml или uv.lock.
# ─────────────────────────────────────────────────────────────────────────────
FROM python:3.12-slim AS deps

RUN apt-get update && apt-get install -y --no-install-recommends \
    libpq-dev gcc && rm -rf /var/lib/apt/lists/*

COPY --from=ghcr.io/astral-sh/uv:latest /uv /bin/uv

WORKDIR /app

# Копируем только lockfile — зависимости ставятся без src.
# При изменении только src этот слой не пересобирается.
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --extra worker --extra consumer --no-install-project

# ─────────────────────────────────────────────────────────────────────────────
#  Stage 2: builder
#  Копирует исходный код и финализирует установку.
#  Инвалидируется только при изменении src/ или alembic.ini.
# ─────────────────────────────────────────────────────────────────────────────
FROM deps AS builder

COPY src/ src/
COPY alembic.ini .
RUN uv sync --frozen --no-dev --extra worker --extra consumer

# ─────────────────────────────────────────────────────────────────────────────
#  Stage 3: runtime
#  CUDA 12.8 + cuDNN 9 — оптимальный рантайм для RTX 5060 Ti (Blackwell sm_120).
#  CUDA 12.6+ — минимум для нативной поддержки Blackwell.
#  CUDA 12.8 — рекомендуется для максимальной производительности sm_120.
#  Хост-драйвер 595.58 (CUDA 13.2) полностью совместим с этим образом.
# ─────────────────────────────────────────────────────────────────────────────
FROM nvcr.io/nvidia/cuda:13.0.0-cudnn-runtime-ubuntu22.04

ENV DEBIAN_FRONTEND=noninteractive

# Устанавливаем Python 3.12 через deadsnakes PPA
RUN apt-get update && apt-get install -y --no-install-recommends \
    software-properties-common ca-certificates gnupg && \
    add-apt-repository ppa:deadsnakes/ppa && \
    apt-get update && apt-get install -y --no-install-recommends \
    python3.12 python3.12-venv libpq5 && \
    rm -rf /var/lib/apt/lists/*

# Симлинки: venv-шебанги из python:3.12-slim ведут на /usr/local/bin/python3.12.
# В Ubuntu этот путь не существует — создаём его.
RUN ln -s /usr/bin/python3.12 /usr/local/bin/python3.12 && \
    ln -s /usr/bin/python3.12 /usr/local/bin/python3 && \
    ln -s /usr/bin/python3.12 /usr/local/bin/python

COPY --from=ghcr.io/astral-sh/uv:latest /uv /bin/uv

WORKDIR /app

COPY --from=builder /app/.venv .venv
COPY --from=builder /app/src src
COPY --from=builder /app/alembic.ini .

ENV PATH="/app/.venv/bin:$PATH"
# Разрешаем доступ ко всем GPU и возможностям для вычислений
ENV NVIDIA_VISIBLE_DEVICES=all
ENV NVIDIA_DRIVER_CAPABILITIES=compute,utility
# Text window limits — configurable via docker-compose or runtime env
ENV DEDUP_MAX_TOKENS=8192
ENV DEDUP_MAX_CLEAN_TEXT_BYTES=1048576
ENV DEDUP_TRUNCATE_TEXT_BYTES=524288
