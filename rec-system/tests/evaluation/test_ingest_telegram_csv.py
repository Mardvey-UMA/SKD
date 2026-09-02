"""Tests for scripts/ingest_telegram_csv.py — RED phase."""
import csv
import io
import os
import uuid
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

# These imports will fail until implementation exists (RED phase)
from scripts.ingest_telegram_csv import (
    RowMapper,
    SamplingStrategy,
    IngestStats,
    StratifiedSampler,
    build_external_id,
    parse_date,
    parse_views,
    clean_content,
)


SOURCE_UUID = str(uuid.uuid4())
SAMPLE_DATE = "2025-11-13 01:30:58"


# ─── Unit: build_external_id ────────────────────────────────────────────────

@pytest.mark.unit
def test_build_external_id_format():
    result = build_external_id("mosnews", "33679212544")
    assert result == "telegram:mosnews:33679212544"


@pytest.mark.unit
def test_build_external_id_strips_whitespace():
    result = build_external_id("  mosnews  ", "  123  ")
    assert result == "telegram:mosnews:123"


# ─── Unit: parse_date ────────────────────────────────────────────────────────

@pytest.mark.unit
def test_parse_date_valid():
    result = parse_date("2025-11-13 01:30:58")
    assert result is not None
    assert result.year == 2025
    assert result.month == 11
    assert result.day == 13


@pytest.mark.unit
def test_parse_date_empty_returns_none():
    result = parse_date("")
    assert result is None


@pytest.mark.unit
def test_parse_date_malformed_returns_none():
    result = parse_date("not-a-date")
    assert result is None


@pytest.mark.unit
def test_parse_date_none_returns_none():
    result = parse_date(None)
    assert result is None


# ─── Unit: parse_views ───────────────────────────────────────────────────────

@pytest.mark.unit
def test_parse_views_integer_string():
    assert parse_views("20245") == 20245


@pytest.mark.unit
def test_parse_views_non_numeric_returns_zero():
    assert parse_views("N/A") == 0


@pytest.mark.unit
def test_parse_views_empty_returns_zero():
    assert parse_views("") == 0


@pytest.mark.unit
def test_parse_views_none_returns_zero():
    assert parse_views(None) == 0


@pytest.mark.unit
def test_parse_views_negative_returns_zero():
    assert parse_views("-1") == 0


# ─── Unit: clean_content ─────────────────────────────────────────────────────

@pytest.mark.unit
def test_clean_content_normal():
    result = clean_content("Hello world")
    assert result == "Hello world"


@pytest.mark.unit
def test_clean_content_strips_whitespace():
    result = clean_content("  hello  ")
    assert result == "hello"


@pytest.mark.unit
def test_clean_content_empty_returns_empty():
    assert clean_content("") == ""


@pytest.mark.unit
def test_clean_content_none_returns_empty():
    assert clean_content(None) == ""


@pytest.mark.unit
def test_clean_content_truncates_at_100k():
    long_text = "a" * 150_000
    result = clean_content(long_text)
    assert len(result) == 100_000


# ─── Unit: RowMapper ─────────────────────────────────────────────────────────

@pytest.mark.unit
def test_row_mapper_valid_row():
    mapper = RowMapper(source_id=SOURCE_UUID, source_type="TELEGRAM_CHANNEL")
    row = {
        "message_id": "123",
        "channel_username": "mosnews",
        "channel_title": "Московская Хроника",
        "content": "Test content that is long enough to pass",
        "post_date": SAMPLE_DATE,
        "channel_url": "https://t.me/mosnews",
        "views": "1000",
    }
    result = mapper.map(row)
    assert result is not None
    assert result["external_id"] == "telegram:mosnews:123"
    assert result["source_id"] == SOURCE_UUID
    assert result["source_type"] == "TELEGRAM_CHANNEL"
    assert result["processing_status"] == "COMPLETED"
    assert result["is_processed_by_rec"] is False
    assert result["is_processed_by_dedup"] is False
    assert result["cleaning_status"] == "COMPLETED"
    assert result["clean_text"] == "Test content that is long enough to pass"
    assert result["clean_text_length"] == len("Test content that is long enough to pass")
    # raw_data must contain channel, publishedAt, content, views, title
    assert result["raw_data"]["channel"] == "mosnews"
    assert result["raw_data"]["views"] == 1000
    assert result["raw_data"]["title"] == "Московская Хроника"
    assert "publishedAt" in result["raw_data"]


@pytest.mark.unit
def test_row_mapper_empty_content_returns_none():
    mapper = RowMapper(source_id=SOURCE_UUID, source_type="TELEGRAM_CHANNEL")
    row = {
        "message_id": "123",
        "channel_username": "mosnews",
        "channel_title": "Test",
        "content": "",
        "post_date": SAMPLE_DATE,
        "channel_url": "https://t.me/mosnews",
        "views": "0",
    }
    result = mapper.map(row)
    assert result is None


@pytest.mark.unit
def test_row_mapper_short_content_returns_none(min_length=50):
    mapper = RowMapper(source_id=SOURCE_UUID, source_type="TELEGRAM_CHANNEL", min_length=50)
    row = {
        "message_id": "123",
        "channel_username": "mosnews",
        "channel_title": "Test",
        "content": "Short",
        "post_date": SAMPLE_DATE,
        "channel_url": "https://t.me/mosnews",
        "views": "0",
    }
    result = mapper.map(row)
    assert result is None


@pytest.mark.unit
def test_row_mapper_malformed_date_still_maps():
    mapper = RowMapper(source_id=SOURCE_UUID, source_type="TELEGRAM_CHANNEL")
    row = {
        "message_id": "999",
        "channel_username": "test_ch",
        "channel_title": "Test Channel",
        "content": "A" * 100,
        "post_date": "not-a-date",
        "channel_url": "https://t.me/test",
        "views": "42",
    }
    result = mapper.map(row)
    # malformed date → still map, use current time
    assert result is not None
    assert result["external_id"] == "telegram:test_ch:999"


@pytest.mark.unit
def test_row_mapper_non_numeric_views():
    mapper = RowMapper(source_id=SOURCE_UUID, source_type="TELEGRAM_CHANNEL")
    row = {
        "message_id": "42",
        "channel_username": "ch",
        "channel_title": "Ch",
        "content": "B" * 100,
        "post_date": SAMPLE_DATE,
        "channel_url": "https://t.me/ch",
        "views": "N/A",
    }
    result = mapper.map(row)
    assert result is not None
    assert result["raw_data"]["views"] == 0


@pytest.mark.unit
def test_row_mapper_very_long_content_truncated():
    mapper = RowMapper(source_id=SOURCE_UUID, source_type="TELEGRAM_CHANNEL")
    row = {
        "message_id": "77",
        "channel_username": "longch",
        "channel_title": "Long",
        "content": "Z" * 200_000,
        "post_date": SAMPLE_DATE,
        "channel_url": "https://t.me/longch",
        "views": "0",
    }
    result = mapper.map(row)
    assert result is not None
    assert len(result["clean_text"]) == 100_000


# ─── Unit: StratifiedSampler ─────────────────────────────────────────────────

@pytest.mark.unit
def test_stratified_sampler_caps_per_channel():
    sampler = StratifiedSampler(cap_per_channel=3, total_limit=100)
    rows = [
        {"channel_username": "ch1", "content": f"msg{i}", "post_date": SAMPLE_DATE,
         "message_id": str(i), "channel_title": "T", "channel_url": "u", "views": "0"}
        for i in range(10)
    ]
    result = sampler.sample(rows)
    assert len(result) == 3


@pytest.mark.unit
def test_stratified_sampler_respects_total_limit():
    sampler = StratifiedSampler(cap_per_channel=100, total_limit=5)
    rows = []
    for ch in ["ch1", "ch2", "ch3"]:
        for i in range(10):
            rows.append({"channel_username": ch, "content": f"msg{i}",
                         "post_date": SAMPLE_DATE, "message_id": str(i),
                         "channel_title": "T", "channel_url": "u", "views": "0"})
    result = sampler.sample(rows)
    assert len(result) <= 5


@pytest.mark.unit
def test_stratified_sampler_distributes_across_channels():
    sampler = StratifiedSampler(cap_per_channel=500, total_limit=9)
    rows = []
    for ch in ["ch1", "ch2", "ch3"]:
        for i in range(10):
            rows.append({"channel_username": ch, "content": f"msg{i}",
                         "post_date": SAMPLE_DATE, "message_id": f"{ch}_{i}",
                         "channel_title": "T", "channel_url": "u", "views": "0"})
    result = sampler.sample(rows)
    channels_in_result = {r["channel_username"] for r in result}
    assert len(channels_in_result) == 3


@pytest.mark.unit
def test_stratified_sampler_takes_all_if_under_cap():
    sampler = StratifiedSampler(cap_per_channel=500, total_limit=1000)
    rows = [
        {"channel_username": "ch1", "content": f"msg{i}", "post_date": SAMPLE_DATE,
         "message_id": str(i), "channel_title": "T", "channel_url": "u", "views": "0"}
        for i in range(10)
    ]
    result = sampler.sample(rows)
    assert len(result) == 10


# ─── Unit: IngestStats ───────────────────────────────────────────────────────

@pytest.mark.unit
def test_ingest_stats_tracks_counts():
    stats = IngestStats()
    stats.record_read()
    stats.record_read()
    stats.record_skipped("empty")
    stats.record_inserted()
    stats.record_duplicate()
    assert stats.total_read == 2
    assert stats.skipped == 1
    assert stats.inserted == 1
    assert stats.duplicates == 1


@pytest.mark.unit
def test_ingest_stats_summary_dict():
    stats = IngestStats()
    stats.record_read()
    stats.record_inserted()
    d = stats.to_dict()
    assert "total_read" in d
    assert "inserted" in d
    assert "duplicates" in d
    assert "skipped" in d


# ─── Integration: full ingestion pipeline ────────────────────────────────────

@pytest.mark.integration
def test_ingest_synthetic_fixture():
    """Ingest 100-row synthetic fixture into testcontainers PG, verify counts."""
    pytest.importorskip("testcontainers")
    from testcontainers.postgres import PostgresContainer
    import asyncio
    import asyncpg

    from scripts.ingest_telegram_csv import IngestPipeline

    # Build synthetic CSV
    rows = []
    channels = ["ch_alpha", "ch_beta", "ch_gamma", "ch_delta"]
    for i in range(100):
        ch = channels[i % len(channels)]
        rows.append({
            "message_id": str(i + 1),
            "channel_username": ch,
            "channel_title": f"Channel {ch}",
            "content": f"Post content number {i} with enough text to pass the min-length filter " * 3,
            "post_date": "2025-10-01 12:00:00",
            "channel_url": f"https://t.me/{ch}",
            "views": str(i * 10),
        })

    async def run(dsn: str):
        conn = await asyncpg.connect(dsn)
        # Minimal schema: just what we need
        await conn.execute("""
            CREATE SCHEMA IF NOT EXISTS config;
            CREATE SCHEMA IF NOT EXISTS data_flow;
            CREATE TABLE IF NOT EXISTS config.sources (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                source_type VARCHAR NOT NULL,
                name VARCHAR NOT NULL,
                url VARCHAR,
                update_frequency_minutes INTEGER NOT NULL DEFAULT 60,
                is_active BOOLEAN NOT NULL DEFAULT true,
                parameters JSONB NOT NULL DEFAULT '{}',
                created_at TIMESTAMP NOT NULL DEFAULT now(),
                updated_at TIMESTAMP NOT NULL DEFAULT now()
            );
            CREATE TABLE IF NOT EXISTS data_flow.raw_content (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                external_id VARCHAR NOT NULL,
                source_id UUID NOT NULL,
                source_type VARCHAR NOT NULL,
                raw_data JSONB NOT NULL,
                raw_media JSONB,
                downloaded_media JSONB,
                processing_status VARCHAR DEFAULT 'PENDING',
                received_at TIMESTAMP NOT NULL DEFAULT now(),
                processed_at TIMESTAMP,
                error_message TEXT,
                retry_count INTEGER NOT NULL DEFAULT 0,
                created_at TIMESTAMP NOT NULL DEFAULT now(),
                updated_at TIMESTAMP NOT NULL DEFAULT now(),
                is_processed_by_dedup BOOLEAN NOT NULL DEFAULT false,
                is_published BOOLEAN NOT NULL DEFAULT false,
                is_processed_by_rec BOOLEAN NOT NULL DEFAULT false,
                clean_html TEXT,
                clean_text TEXT,
                preview_text TEXT,
                clean_text_length INTEGER,
                cleaning_status VARCHAR,
                cleaning_error TEXT,
                cleaning_attempts INTEGER NOT NULL DEFAULT 0,
                cleaned_at TIMESTAMPTZ,
                CONSTRAINT uk_raw_content_source_external UNIQUE (source_type, external_id)
            );
        """)
        # Insert a source row
        src_id = str(uuid.uuid4())
        await conn.execute(
            "INSERT INTO config.sources (id, source_type, name) VALUES ($1, 'TELEGRAM', 'test_source')",
            uuid.UUID(src_id),
        )
        await conn.close()
        return src_id

    with PostgresContainer("postgres:17") as pg:
        dsn = pg.get_connection_url().replace("postgresql+psycopg2", "postgresql")
        src_id = asyncio.get_event_loop().run_until_complete(run(dsn))

        pipeline = IngestPipeline(
            db_dsn=dsn,
            source_id=src_id,
            source_type="TELEGRAM_CHANNEL",
            batch_size=50,
            min_length=10,
            skip_empty=True,
        )
        stats = asyncio.get_event_loop().run_until_complete(
            pipeline.ingest_rows(rows)
        )

        assert stats.inserted == 100
        assert stats.duplicates == 0

        # Verify in DB
        async def verify(dsn):
            conn = await asyncpg.connect(dsn)
            count = await conn.fetchval(
                "SELECT COUNT(*) FROM data_flow.raw_content WHERE source_id=$1",
                uuid.UUID(src_id),
            )
            await conn.close()
            return count

        count = asyncio.get_event_loop().run_until_complete(verify(dsn))
        assert count == 100


@pytest.mark.integration
def test_ingest_respects_stratified_cap():
    """With cap=3 per channel and 4 channels × 10 rows = only 12 inserted (4×3)."""
    pytest.importorskip("testcontainers")
    from testcontainers.postgres import PostgresContainer
    import asyncio
    import asyncpg
    from scripts.ingest_telegram_csv import IngestPipeline

    rows = []
    for ch in ["ch1", "ch2", "ch3", "ch4"]:
        for i in range(10):
            rows.append({
                "message_id": f"{ch}_{i}",
                "channel_username": ch,
                "channel_title": f"Ch {ch}",
                "content": f"Content {i} of channel {ch} with enough chars " * 3,
                "post_date": "2025-10-01 12:00:00",
                "channel_url": f"https://t.me/{ch}",
                "views": "0",
            })

    async def setup(dsn):
        conn = await asyncpg.connect(dsn)
        await conn.execute("""
            CREATE SCHEMA IF NOT EXISTS config;
            CREATE SCHEMA IF NOT EXISTS data_flow;
            CREATE TABLE IF NOT EXISTS config.sources (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                source_type VARCHAR NOT NULL,
                name VARCHAR NOT NULL,
                url VARCHAR,
                update_frequency_minutes INTEGER NOT NULL DEFAULT 60,
                is_active BOOLEAN NOT NULL DEFAULT true,
                parameters JSONB NOT NULL DEFAULT '{}',
                created_at TIMESTAMP NOT NULL DEFAULT now(),
                updated_at TIMESTAMP NOT NULL DEFAULT now()
            );
            CREATE TABLE IF NOT EXISTS data_flow.raw_content (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                external_id VARCHAR NOT NULL,
                source_id UUID NOT NULL,
                source_type VARCHAR NOT NULL,
                raw_data JSONB NOT NULL,
                raw_media JSONB,
                downloaded_media JSONB,
                processing_status VARCHAR DEFAULT 'PENDING',
                received_at TIMESTAMP NOT NULL DEFAULT now(),
                processed_at TIMESTAMP,
                error_message TEXT,
                retry_count INTEGER NOT NULL DEFAULT 0,
                created_at TIMESTAMP NOT NULL DEFAULT now(),
                updated_at TIMESTAMP NOT NULL DEFAULT now(),
                is_processed_by_dedup BOOLEAN NOT NULL DEFAULT false,
                is_published BOOLEAN NOT NULL DEFAULT false,
                is_processed_by_rec BOOLEAN NOT NULL DEFAULT false,
                clean_html TEXT,
                clean_text TEXT,
                preview_text TEXT,
                clean_text_length INTEGER,
                cleaning_status VARCHAR,
                cleaning_error TEXT,
                cleaning_attempts INTEGER NOT NULL DEFAULT 0,
                cleaned_at TIMESTAMPTZ,
                CONSTRAINT uk_raw_content_source_external UNIQUE (source_type, external_id)
            );
        """)
        src_id = str(uuid.uuid4())
        await conn.execute(
            "INSERT INTO config.sources (id, source_type, name) VALUES ($1, 'TELEGRAM', 'test')",
            uuid.UUID(src_id),
        )
        await conn.close()
        return src_id

    with PostgresContainer("postgres:17") as pg:
        dsn = pg.get_connection_url().replace("postgresql+psycopg2", "postgresql")
        src_id = asyncio.get_event_loop().run_until_complete(setup(dsn))

        pipeline = IngestPipeline(
            db_dsn=dsn,
            source_id=src_id,
            source_type="TELEGRAM_CHANNEL",
            batch_size=50,
            min_length=10,
            skip_empty=True,
            cap_per_channel=3,
            total_limit=1000,
            sample_strategy="stratified_by_channel",
        )
        stats = asyncio.get_event_loop().run_until_complete(
            pipeline.ingest_rows(rows)
        )

        assert stats.inserted == 12  # 4 channels × 3 cap


@pytest.mark.integration
def test_ingest_handles_duplicates_gracefully():
    """Second ingestion of same rows → all duplicates, no error."""
    pytest.importorskip("testcontainers")
    from testcontainers.postgres import PostgresContainer
    import asyncio
    import asyncpg
    from scripts.ingest_telegram_csv import IngestPipeline

    rows = [
        {
            "message_id": f"{i}",
            "channel_username": "ch_test",
            "channel_title": "Test",
            "content": f"Unique content for post {i} " * 5,
            "post_date": "2025-10-01 12:00:00",
            "channel_url": "https://t.me/ch_test",
            "views": "10",
        }
        for i in range(20)
    ]

    async def setup(dsn):
        conn = await asyncpg.connect(dsn)
        await conn.execute("""
            CREATE SCHEMA IF NOT EXISTS config;
            CREATE SCHEMA IF NOT EXISTS data_flow;
            CREATE TABLE IF NOT EXISTS config.sources (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                source_type VARCHAR NOT NULL,
                name VARCHAR NOT NULL,
                url VARCHAR,
                update_frequency_minutes INTEGER NOT NULL DEFAULT 60,
                is_active BOOLEAN NOT NULL DEFAULT true,
                parameters JSONB NOT NULL DEFAULT '{}',
                created_at TIMESTAMP NOT NULL DEFAULT now(),
                updated_at TIMESTAMP NOT NULL DEFAULT now()
            );
            CREATE TABLE IF NOT EXISTS data_flow.raw_content (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                external_id VARCHAR NOT NULL,
                source_id UUID NOT NULL,
                source_type VARCHAR NOT NULL,
                raw_data JSONB NOT NULL,
                raw_media JSONB,
                downloaded_media JSONB,
                processing_status VARCHAR DEFAULT 'PENDING',
                received_at TIMESTAMP NOT NULL DEFAULT now(),
                processed_at TIMESTAMP,
                error_message TEXT,
                retry_count INTEGER NOT NULL DEFAULT 0,
                created_at TIMESTAMP NOT NULL DEFAULT now(),
                updated_at TIMESTAMP NOT NULL DEFAULT now(),
                is_processed_by_dedup BOOLEAN NOT NULL DEFAULT false,
                is_published BOOLEAN NOT NULL DEFAULT false,
                is_processed_by_rec BOOLEAN NOT NULL DEFAULT false,
                clean_html TEXT,
                clean_text TEXT,
                preview_text TEXT,
                clean_text_length INTEGER,
                cleaning_status VARCHAR,
                cleaning_error TEXT,
                cleaning_attempts INTEGER NOT NULL DEFAULT 0,
                cleaned_at TIMESTAMPTZ,
                CONSTRAINT uk_raw_content_source_external UNIQUE (source_type, external_id)
            );
        """)
        src_id = str(uuid.uuid4())
        await conn.execute(
            "INSERT INTO config.sources (id, source_type, name) VALUES ($1, 'TELEGRAM', 'test')",
            uuid.UUID(src_id),
        )
        await conn.close()
        return src_id

    with PostgresContainer("postgres:17") as pg:
        dsn = pg.get_connection_url().replace("postgresql+psycopg2", "postgresql")
        src_id = asyncio.get_event_loop().run_until_complete(setup(dsn))

        pipeline = IngestPipeline(
            db_dsn=dsn,
            source_id=src_id,
            source_type="TELEGRAM_CHANNEL",
            batch_size=50,
            min_length=10,
            skip_empty=True,
        )

        loop = asyncio.get_event_loop()
        stats1 = loop.run_until_complete(pipeline.ingest_rows(rows))
        assert stats1.inserted == 20

        stats2 = loop.run_until_complete(pipeline.ingest_rows(rows))
        assert stats2.duplicates == 20
        assert stats2.inserted == 0
