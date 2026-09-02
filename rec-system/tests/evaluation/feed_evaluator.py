"""FeedEvaluator — Phase 3 of the rec-system evaluation harness.

Wraps GenerateFeedUseCase to produce a structured FeedResult with all
metadata needed for downstream analysis (Phase 4 metrics, Phase 5 LLM judge).

Design adaptations from spec (documented here):
- `published_repo` parameter does not exist as a separate class; instead we
  accept `session_factory` and query data_flow.published_content and
  data_flow.raw_content directly via raw SQLAlchemy selects.
- FeedItem.title comes from raw_content.raw_data["title"] (not posts_features,
  which stores no title column).
- FeedItem.content_snippet uses raw_content.clean_text preferring over
  raw_data["content"]; may be empty in eval environments seeded without text.
- explain=True is not supported until Phase 8; scoring_breakdown stays None.
"""
from __future__ import annotations

import json
import logging
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Callable
from uuid import UUID

from sqlalchemy import select

from src.application.dto.generate_feed import GenerateFeedRequest
from src.domain.interfaces.content_repository import ContentRepository
from src.infrastructure.persistence.models.published_content import PublishedContentModel
from src.infrastructure.persistence.models.raw_content import RawContentModel
from tests.evaluation.behavior_simulator import SimulatedUserState

logger = logging.getLogger(__name__)

_SNIPPET_LEN = 300
_SNIPPET_ELLIPSIS = "…"


@dataclass
class FeedItem:
    position: int                       # 1-N (1-indexed position in returned feed)
    post_id: UUID                       # published_content.id
    raw_content_id: UUID                # posts_features.post_id == raw_content.id
    title: str
    content_snippet: str                # first 300 chars of clean text + "…" if truncated
    topics: list[tuple[str, float]]     # [(topic_name, score)] up to 3
    sentiment: tuple[str, float]        # (label, score)
    entities: dict[str, list[str]]      # {"PERSON": [...], "ORG": [...], "LOC": [...]}
    embedding: list[float]              # 312-dim rubert-tiny2; empty list if absent
    text_metrics: dict[str, Any]        # word_count, reading_time, complexity
    source_id: UUID
    age_hours: float                    # (now - published_content.published_at) / 3600
    scoring_breakdown: dict | None = None  # populated only when explain=True (Phase 8)


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
    metadata: dict[str, Any] = field(default_factory=dict)

    def to_json(self) -> str:
        """Serialize to JSON. UUIDs as strings, datetimes as ISO 8601 with Z suffix."""
        d = {
            "user_id": str(self.user_id),
            "persona_id": self.persona_id,
            "version": self.version,
            "seed": self.seed,
            "run_number": self.run_number,
            "generated_at": _dt_to_str(self.generated_at),
            "items": [_item_to_dict(item) for item in self.items],
            "request_latency_ms": self.request_latency_ms,
            "count": self.count,
            "metadata": self.metadata,
        }
        return json.dumps(d, ensure_ascii=False)

    @classmethod
    def from_json(cls, s: str) -> "FeedResult":
        """Deserialize from JSON string produced by to_json()."""
        d = json.loads(s)
        return cls(
            user_id=UUID(d["user_id"]),
            persona_id=d["persona_id"],
            version=d["version"],
            seed=d["seed"],
            run_number=d["run_number"],
            generated_at=_str_to_dt(d["generated_at"]),
            items=[_dict_to_item(i) for i in d["items"]],
            request_latency_ms=d["request_latency_ms"],
            count=d["count"],
            metadata=d.get("metadata", {}),
        )


class FeedEvaluator:
    """Wraps GenerateFeedUseCase to produce a FeedResult with per-item metadata.

    The `session_factory` parameter (called `published_repo` in the spec) is used
    for two supplementary lookups not available via ContentRepository:
    1. Reverse map: published_content.id → raw_content_id + published_at
    2. Raw text: raw_content.raw_data["title"] + clean_text for content_snippet
    """

    def __init__(
        self,
        generate_feed_use_case,
        content_repo: ContentRepository,
        session_factory: Callable,
    ) -> None:
        self._generate_feed_use_case = generate_feed_use_case
        self._content_repo = content_repo
        self._session_factory = session_factory

    async def run(
        self,
        synthetic_user: SimulatedUserState,
        count: int = 30,
        explain: bool = False,
        version: str = "baseline",
        run_number: int = 1,
    ) -> FeedResult:
        """Generate a feed for the synthetic user and return a structured FeedResult."""
        if explain:
            logger.warning(
                "explain=True is not supported until Phase 8; "
                "scoring_breakdown will be None for all items"
            )

        now = datetime.now(timezone.utc)

        t0 = time.monotonic()
        request = GenerateFeedRequest(user_id=synthetic_user.user_id, feed_size=count)
        response = await self._generate_feed_use_case.execute(request)
        latency_ms = (time.monotonic() - t0) * 1000

        pub_ids = [UUID(entry["post_id"]) for entry in response.feed]
        pub_data = await self._fetch_published_data(pub_ids)

        raw_ids = [
            pub_data[pid]["raw_content_id"]
            for pid in pub_ids
            if pid in pub_data and pub_data[pid]["raw_content_id"] is not None
        ]

        features_list = await self._content_repo.get_by_ids(raw_ids)
        features_map = {f.post_id: f for f in features_list}

        raw_text = await self._fetch_raw_content_data(raw_ids)

        items: list[FeedItem] = []
        for i, feed_entry in enumerate(response.feed):
            pub_id = UUID(feed_entry["post_id"])
            if pub_id not in pub_data:
                continue
            p_info = pub_data[pub_id]
            raw_id = p_info["raw_content_id"]
            if raw_id is None:
                continue
            published_at = p_info["published_at"]

            f = features_map.get(raw_id)
            r = raw_text.get(raw_id, {})

            title = r.get("title") or (f.title if f else None) or ""
            content_text = r.get("content") or (f.content if f else "") or ""
            snippet = _make_snippet(content_text)

            topics: list[tuple[str, float]] = []
            if f:
                if f.topic_1 and f.topic_1_score is not None:
                    topics.append((f.topic_1, f.topic_1_score))
                if f.topic_2 and f.topic_2_score is not None:
                    topics.append((f.topic_2, f.topic_2_score))
                if f.topic_3 and f.topic_3_score is not None:
                    topics.append((f.topic_3, f.topic_3_score))

            sentiment: tuple[str, float] = (
                (f.sentiment, f.sentiment_score or 0.0)
                if f and f.sentiment
                else ("NEUTRAL", 0.0)
            )

            entities: dict[str, list[str]] = {}
            if f:
                if f.entities_persons:
                    entities["PERSON"] = list(f.entities_persons)
                if f.entities_organizations:
                    entities["ORG"] = list(f.entities_organizations)
                if f.entities_locations:
                    entities["LOC"] = list(f.entities_locations)

            embedding = [float(x) for x in f.embedding] if f and f.embedding is not None else []
            if not embedding:
                logger.warning("post %s has no embedding; using empty list", raw_id)

            text_metrics: dict[str, Any] = {}
            if f:
                if f.word_count is not None:
                    text_metrics["word_count"] = f.word_count
                if f.reading_time is not None:
                    text_metrics["reading_time"] = f.reading_time
                if f.complexity is not None:
                    text_metrics["complexity"] = f.complexity

            # Use posts_features.processed_at (when content entered the rec
            # pipeline) rather than published_content.published_at (original
            # Telegram publication date, can be years old for CSV-ingested corpora).
            # Aligns age_hours semantics with the 48h retrieval filter that also
            # uses processed_at as its freshness reference.
            processed_at_ts = f.processed_at if f else None
            if processed_at_ts is not None:
                proc_at_aware = (
                    processed_at_ts.replace(tzinfo=timezone.utc)
                    if processed_at_ts.tzinfo is None
                    else processed_at_ts
                )
                age_hours = max(0.0, (now - proc_at_aware).total_seconds() / 3600)
            elif published_at is not None:
                pub_at_aware = (
                    published_at.replace(tzinfo=timezone.utc)
                    if published_at.tzinfo is None
                    else published_at
                )
                age_hours = max(0.0, (now - pub_at_aware).total_seconds() / 3600)
            else:
                age_hours = 0.0

            source_id = f.source_id if f and f.source_id else UUID(int=0)

            items.append(
                FeedItem(
                    position=i + 1,
                    post_id=pub_id,
                    raw_content_id=raw_id,
                    title=title,
                    content_snippet=snippet,
                    topics=topics,
                    sentiment=sentiment,
                    entities=entities,
                    embedding=embedding,
                    text_metrics=text_metrics,
                    source_id=source_id,
                    age_hours=age_hours,
                    scoring_breakdown=None,
                )
            )

        return FeedResult(
            user_id=synthetic_user.user_id,
            persona_id=synthetic_user.persona_id,
            version=version,
            seed=synthetic_user.seed,
            run_number=run_number,
            generated_at=now,
            items=items,
            request_latency_ms=latency_ms,
            count=len(items),
            metadata={},
        )

    async def _fetch_published_data(self, pub_ids: list[UUID]) -> dict[UUID, dict]:
        """Return {published_content.id: {raw_content_id, published_at}} for given IDs."""
        if not pub_ids:
            return {}
        async with self._session_factory() as session:
            stmt = select(PublishedContentModel).where(
                PublishedContentModel.id.in_(pub_ids)
            )
            result = await session.execute(stmt)
            rows = result.scalars().all()
        return {
            row.id: {
                "raw_content_id": row.content_id,
                "published_at": row.published_at,
            }
            for row in rows
        }

    async def _fetch_raw_content_data(self, raw_ids: list[UUID]) -> dict[UUID, dict]:
        """Return {raw_content.id: {title, content}} for given IDs."""
        if not raw_ids:
            return {}
        async with self._session_factory() as session:
            stmt = select(
                RawContentModel.id,
                RawContentModel.raw_data,
                RawContentModel.clean_text,
            ).where(RawContentModel.id.in_(raw_ids))
            result = await session.execute(stmt)
            rows = result.all()
        out: dict[UUID, dict] = {}
        for row in rows:
            raw_data = row.raw_data or {}
            title = raw_data.get("title") or ""
            content = row.clean_text or raw_data.get("content") or ""
            out[row.id] = {"title": title, "content": content}
        return out


# ---------------------------------------------------------------------------
# Pure helpers
# ---------------------------------------------------------------------------


def _make_snippet(text: str) -> str:
    """Return first _SNIPPET_LEN chars + ellipsis if text is longer."""
    if not text:
        return ""
    if len(text) <= _SNIPPET_LEN:
        return text
    return text[:_SNIPPET_LEN] + _SNIPPET_ELLIPSIS


def _dt_to_str(dt: datetime) -> str:
    return dt.strftime("%Y-%m-%dT%H:%M:%S.%f") + "Z"


def _str_to_dt(s: str) -> datetime:
    return datetime.fromisoformat(s.rstrip("Z")).replace(tzinfo=timezone.utc)


def _item_to_dict(item: FeedItem) -> dict:
    return {
        "position": item.position,
        "post_id": str(item.post_id),
        "raw_content_id": str(item.raw_content_id),
        "title": item.title,
        "content_snippet": item.content_snippet,
        "topics": [[t, s] for t, s in item.topics],
        "sentiment": list(item.sentiment),
        "entities": item.entities,
        "embedding": item.embedding,
        "text_metrics": item.text_metrics,
        "source_id": str(item.source_id),
        "age_hours": item.age_hours,
        "scoring_breakdown": item.scoring_breakdown,
    }


def _dict_to_item(d: dict) -> FeedItem:
    return FeedItem(
        position=d["position"],
        post_id=UUID(d["post_id"]),
        raw_content_id=UUID(d["raw_content_id"]),
        title=d["title"],
        content_snippet=d["content_snippet"],
        topics=[(t, s) for t, s in d["topics"]],
        sentiment=tuple(d["sentiment"]),
        entities=d["entities"],
        embedding=d["embedding"],
        text_metrics=d["text_metrics"],
        source_id=UUID(d["source_id"]),
        age_hours=d["age_hours"],
        scoring_breakdown=d.get("scoring_breakdown"),
    )
