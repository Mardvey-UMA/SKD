"""Telegram CSV ingestion script for evaluation harness corpus.

Usage:
    uv run python scripts/ingest_telegram_csv.py \
        --csv-path /path/to/telegram_posts.csv \
        --limit 30000 \
        --source-id <UUID> \
        --sample-strategy stratified_by_channel \
        --min-length 50
"""
from __future__ import annotations

import argparse
import asyncio
import csv
import json
import logging
import random
import sys
import uuid
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterator, Optional

import asyncpg

logger = logging.getLogger(__name__)

SOURCE_TYPE = "TELEGRAM_CHANNEL"
MAX_CONTENT_LENGTH = 100_000


# ─── Pure helpers ────────────────────────────────────────────────────────────

def build_external_id(channel_username: str, message_id: str) -> str:
    return f"telegram:{channel_username.strip()}:{message_id.strip()}"


def parse_date(value: Optional[str]) -> Optional[datetime]:
    if not value:
        return None
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d"):
        try:
            return datetime.strptime(value.strip(), fmt).replace(tzinfo=timezone.utc)
        except (ValueError, AttributeError):
            continue
    return None


def parse_views(value: Optional[str]) -> int:
    try:
        v = int(str(value).strip())
        return max(0, v)
    except (ValueError, TypeError):
        return 0


def clean_content(value: Optional[str]) -> str:
    if not value:
        return ""
    text = value.strip()
    return text[:MAX_CONTENT_LENGTH]


# ─── Row mapper ──────────────────────────────────────────────────────────────

class RowMapper:
    def __init__(
        self,
        source_id: str,
        source_type: str = SOURCE_TYPE,
        min_length: int = 0,
        skip_empty: bool = True,
    ) -> None:
        self.source_id = source_id
        self.source_type = source_type
        self.min_length = min_length
        self.skip_empty = skip_empty

    def map(self, row: dict[str, Any]) -> Optional[dict[str, Any]]:
        content = clean_content(row.get("content", ""))
        if not content:
            return None
        if len(content) < self.min_length:
            return None

        channel = (row.get("channel_username") or "").strip()
        message_id = (row.get("message_id") or "").strip()
        channel_title = (row.get("channel_title") or "").strip()
        channel_url = (row.get("channel_url") or "").strip()
        views = parse_views(row.get("views"))

        post_date = parse_date(row.get("post_date"))
        now = datetime.now(tz=timezone.utc)
        received_at = post_date or now

        external_id = build_external_id(channel, message_id)

        raw_data = {
            "channel": channel,
            "title": channel_title or None,
            "content": content,
            "publishedAt": received_at.isoformat(),
            "channel_url": channel_url,
            "views": views,
        }

        return {
            "external_id": external_id,
            "source_id": self.source_id,
            "source_type": self.source_type,
            "raw_data": raw_data,  # dict; serialised to JSONB in _to_tuple
            "processing_status": "COMPLETED",
            "received_at": received_at.replace(tzinfo=None),  # asyncpg needs naive for TIMESTAMP
            "is_processed_by_rec": False,
            "is_processed_by_dedup": False,
            "clean_text": content,
            "clean_text_length": len(content),
            "cleaning_status": "COMPLETED",
            "cleaned_at": received_at,
        }


# ─── Stratified sampler ──────────────────────────────────────────────────────

class SamplingStrategy:
    HEAD = "head"
    RANDOM = "random"
    RECENT = "recent"
    STRATIFIED_BY_CHANNEL = "stratified_by_channel"


class StratifiedSampler:
    def __init__(self, cap_per_channel: int = 500, total_limit: int = 30_000) -> None:
        self.cap_per_channel = cap_per_channel
        self.total_limit = total_limit

    def sample(self, rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
        by_channel: dict[str, list] = defaultdict(list)
        for row in rows:
            ch = row.get("channel_username", "unknown")
            by_channel[ch].append(row)

        result: list[dict[str, Any]] = []
        channels = list(by_channel.keys())
        random.shuffle(channels)

        # Round-robin across channels up to cap
        channel_iters = {ch: iter(rows_list) for ch, rows_list in by_channel.items()}
        channel_counts: dict[str, int] = defaultdict(int)

        added = True
        while added and len(result) < self.total_limit:
            added = False
            for ch in channels:
                if len(result) >= self.total_limit:
                    break
                if channel_counts[ch] >= self.cap_per_channel:
                    continue
                try:
                    row = next(channel_iters[ch])
                    result.append(row)
                    channel_counts[ch] += 1
                    added = True
                except StopIteration:
                    pass

        return result[: self.total_limit]


# ─── Stats ────────────────────────────────────────────────────────────────────

@dataclass
class IngestStats:
    total_read: int = 0
    skipped: int = 0
    inserted: int = 0
    duplicates: int = 0
    errors: int = 0
    skip_reasons: dict[str, int] = field(default_factory=dict)

    def record_read(self) -> None:
        self.total_read += 1

    def record_skipped(self, reason: str = "unknown") -> None:
        self.skipped += 1
        self.skip_reasons[reason] = self.skip_reasons.get(reason, 0) + 1

    def record_inserted(self) -> None:
        self.inserted += 1

    def record_duplicate(self) -> None:
        self.duplicates += 1

    def record_error(self) -> None:
        self.errors += 1

    def to_dict(self) -> dict[str, Any]:
        return {
            "total_read": self.total_read,
            "skipped": self.skipped,
            "inserted": self.inserted,
            "duplicates": self.duplicates,
            "errors": self.errors,
            "skip_reasons": self.skip_reasons,
        }


# ─── Pipeline ────────────────────────────────────────────────────────────────

INSERT_SQL = """
    INSERT INTO data_flow.raw_content
        (external_id, source_id, source_type, raw_data, processing_status,
         received_at, is_processed_by_rec, is_processed_by_dedup,
         clean_text, clean_text_length, cleaning_status, cleaned_at)
    VALUES
        ($1, $2, $3, $4::jsonb, $5, $6, $7, $8, $9, $10, $11, $12)
    ON CONFLICT (source_type, external_id) DO NOTHING
"""


class IngestPipeline:
    def __init__(
        self,
        db_dsn: str,
        source_id: str,
        source_type: str = SOURCE_TYPE,
        batch_size: int = 1000,
        min_length: int = 50,
        skip_empty: bool = True,
        cap_per_channel: int = 500,
        total_limit: int = 30_000,
        sample_strategy: str = SamplingStrategy.STRATIFIED_BY_CHANNEL,
        dry_run: bool = False,
    ) -> None:
        self.db_dsn = db_dsn
        self.source_id = source_id
        self.source_type = source_type
        self.batch_size = batch_size
        self.min_length = min_length
        self.skip_empty = skip_empty
        self.cap_per_channel = cap_per_channel
        self.total_limit = total_limit
        self.sample_strategy = sample_strategy
        self.dry_run = dry_run
        self.mapper = RowMapper(
            source_id=source_id,
            source_type=source_type,
            min_length=min_length,
            skip_empty=skip_empty,
        )

    async def ingest_rows(self, rows: list[dict[str, Any]]) -> IngestStats:
        stats = IngestStats()

        # Apply sampling
        sampled = self._apply_sampling(rows)

        if self.dry_run:
            for row in sampled:
                stats.record_read()
                mapped = self.mapper.map(row)
                if mapped is None:
                    stats.record_skipped("filtered")
                else:
                    stats.record_inserted()
            return stats

        conn = await asyncpg.connect(self.db_dsn)
        try:
            batch: list[tuple] = []
            for row in sampled:
                stats.record_read()
                mapped = self.mapper.map(row)
                if mapped is None:
                    stats.record_skipped("filtered")
                    continue
                batch.append(self._to_tuple(mapped))
                if len(batch) >= self.batch_size:
                    n_inserted, n_dup = await self._flush(conn, batch)
                    stats.inserted += n_inserted
                    stats.duplicates += n_dup
                    batch = []
                    logger.info(
                        "Flushed batch — inserted=%d duplicates=%d total_inserted=%d",
                        n_inserted, n_dup, stats.inserted,
                    )

            if batch:
                n_inserted, n_dup = await self._flush(conn, batch)
                stats.inserted += n_inserted
                stats.duplicates += n_dup
        finally:
            await conn.close()

        return stats

    def _apply_sampling(self, rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
        if self.sample_strategy == SamplingStrategy.STRATIFIED_BY_CHANNEL:
            sampler = StratifiedSampler(
                cap_per_channel=self.cap_per_channel,
                total_limit=self.total_limit,
            )
            return sampler.sample(rows)
        elif self.sample_strategy == SamplingStrategy.HEAD:
            return rows[: self.total_limit]
        elif self.sample_strategy == SamplingStrategy.RANDOM:
            shuffled = list(rows)
            random.shuffle(shuffled)
            return shuffled[: self.total_limit]
        elif self.sample_strategy == SamplingStrategy.RECENT:
            # sort by post_date DESC
            def _key(r):
                return r.get("post_date") or ""
            return sorted(rows, key=_key, reverse=True)[: self.total_limit]
        return rows[: self.total_limit]

    @staticmethod
    def _to_tuple(mapped: dict[str, Any]) -> tuple:
        return (
            mapped["external_id"],
            uuid.UUID(mapped["source_id"]),
            mapped["source_type"],
            json.dumps(mapped["raw_data"], ensure_ascii=False),
            mapped["processing_status"],
            mapped["received_at"],
            mapped["is_processed_by_rec"],
            mapped["is_processed_by_dedup"],
            mapped["clean_text"],
            mapped["clean_text_length"],
            mapped["cleaning_status"],
            mapped["cleaned_at"],
        )

    async def _flush(self, conn, batch: list[tuple]) -> tuple[int, int]:
        inserted = 0
        duplicates = 0
        async with conn.transaction():
            for record in batch:
                result = await conn.execute(INSERT_SQL, *record)
                # result is "INSERT 0 N" — N=1 means inserted, N=0 means conflict
                n = int(result.split()[-1])
                if n == 1:
                    inserted += 1
                else:
                    duplicates += 1
        return inserted, duplicates


# ─── CSV reader ──────────────────────────────────────────────────────────────

def stream_csv(csv_path: str) -> Iterator[dict[str, str]]:
    with open(csv_path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        yield from reader


def load_csv_all(csv_path: str) -> list[dict[str, str]]:
    return list(stream_csv(csv_path))


# ─── CLI ─────────────────────────────────────────────────────────────────────

def parse_args(argv=None):
    parser = argparse.ArgumentParser(description="Ingest telegram_posts.csv into data_flow.raw_content")
    parser.add_argument("--csv-path", default="/home/mattew/SKD/telegram_posts.csv")
    parser.add_argument("--limit", type=int, default=30_000)
    parser.add_argument("--source-id", required=True)
    parser.add_argument("--batch-size", type=int, default=1_000)
    parser.add_argument("--skip-empty", action="store_true", default=True)
    parser.add_argument("--min-length", type=int, default=50)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument(
        "--sample-strategy",
        choices=["head", "random", "recent", "stratified_by_channel"],
        default="stratified_by_channel",
    )
    parser.add_argument("--cap-per-channel", type=int, default=500)
    parser.add_argument(
        "--db-url",
        default=None,
        help="PostgreSQL DSN (asyncpg format). Falls back to DATABASE_URL env var.",
    )
    return parser.parse_args(argv)


async def main_async(args) -> IngestStats:
    import os

    db_url = args.db_url or os.environ.get(
        "DATABASE_URL",
        "postgresql://postgres:postgres@localhost:30432/content_agg_db",
    )
    # asyncpg doesn't accept +asyncpg scheme
    db_url = db_url.replace("postgresql+asyncpg://", "postgresql://")

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
    )

    logger.info("Loading CSV: %s", args.csv_path)
    rows = load_csv_all(args.csv_path)
    logger.info("CSV loaded: %d rows", len(rows))

    pipeline = IngestPipeline(
        db_dsn=db_url,
        source_id=args.source_id,
        source_type=SOURCE_TYPE,
        batch_size=args.batch_size,
        min_length=args.min_length,
        skip_empty=args.skip_empty,
        cap_per_channel=args.cap_per_channel,
        total_limit=args.limit,
        sample_strategy=args.sample_strategy,
        dry_run=args.dry_run,
    )

    stats = await pipeline.ingest_rows(rows)
    logger.info("Done: %s", stats.to_dict())
    return stats


def main():
    args = parse_args()
    stats = asyncio.run(main_async(args))
    print(json.dumps(stats.to_dict(), indent=2))
    return 0 if stats.errors == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
