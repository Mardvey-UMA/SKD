"""PostgreSQL implementation of ContentRepository.

Polls data_flow.raw_content (processing_status='COMPLETED', is_processed_by_rec=false,
clean_text IS NOT NULL) and reads pre-cleaned text from clean_text directly (T5).
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Callable, List, Optional
from uuid import UUID

from sqlalchemy import func, select, update
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from src.domain.entities.content_features import ContentFeatures
from src.domain.interfaces.content_repository import ContentRepository
from src.infrastructure.persistence.models.article import ArticleModel
from src.infrastructure.persistence.models.posts_features import PostsFeaturesModel
from src.infrastructure.persistence.models.published_content import PublishedContentModel
from src.infrastructure.persistence.models.raw_content import RawContentModel
from src.infrastructure.persistence.models.similarity import SimilarityModel


class _UnionFind:
    """Union-Find with path compression for dedup clustering."""

    def __init__(self) -> None:
        self._parent: dict[int, int] = {}

    def find(self, x: int) -> int:
        if x not in self._parent:
            self._parent[x] = x
        if self._parent[x] != x:
            self._parent[x] = self.find(self._parent[x])
        return self._parent[x]

    def union(self, x: int, y: int) -> None:
        px, py = self.find(x), self.find(y)
        if px != py:
            self._parent[px] = py


def _parse_published_at(raw_data: dict) -> datetime:
    """Parse publishedAt from JSONB: ISO 8601 string or epoch millis."""
    value = raw_data.get("publishedAt") if raw_data else None
    if not value:
        return datetime.now(tz=timezone.utc)
    if isinstance(value, (int, float)):
        return datetime.fromtimestamp(value / 1000, tz=timezone.utc)
    try:
        dt = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
        return dt
    except ValueError:
        return datetime.now(tz=timezone.utc)


def _map_raw_to_entity(row: RawContentModel) -> ContentFeatures:
    """Map a RawContentModel row to a ContentFeatures entity.

    T5: reads pre-cleaned text from row.clean_text directly (no HTML stripping).
    """
    raw_data: dict = row.raw_data or {}
    title: Optional[str] = raw_data.get("title")
    content: str = row.clean_text or ""  # T5: use pre-cleaned text, not raw_data['content']
    post_date: datetime = _parse_published_at(raw_data)

    return ContentFeatures(
        post_id=row.id,
        content=content,
        post_date=post_date,
        title=title,
        source_id=row.source_id,
        source_type=row.source_type,
    )


def _map_features_row(row: PostsFeaturesModel) -> ContentFeatures:
    """Map a PostsFeaturesModel row to a ContentFeatures entity."""
    return ContentFeatures(
        post_id=row.post_id,
        content="",
        post_date=row.processed_at or datetime.now(tz=timezone.utc),
        source_id=row.source_id,
        source_type=row.source_type,
        text_length=row.text_length,
        word_count=row.word_count,
        reading_time=row.reading_time,
        complexity=row.complexity,
        is_short_form=row.is_short_form,
        is_long_form=row.is_long_form,
        topic_1=row.topic_1,
        topic_1_score=row.topic_1_score,
        topic_2=row.topic_2,
        topic_2_score=row.topic_2_score,
        topic_3=row.topic_3,
        topic_3_score=row.topic_3_score,
        sentiment=row.sentiment,
        sentiment_score=row.sentiment_score,
        entities_persons=row.entities_persons or [],
        entities_organizations=row.entities_organizations or [],
        entities_locations=row.entities_locations or [],
        embedding=list(row.embedding) if row.embedding is not None else None,
        processed_at=row.processed_at,
    )


class PgContentRepository(ContentRepository):
    """PostgreSQL adapter for content features and raw posts."""

    def __init__(self, session_factory: Callable[[], AsyncSession]) -> None:
        self._session_factory = session_factory

    async def get_unprocessed(self, batch_size: int) -> List[ContentFeatures]:
        """Return up to batch_size unprocessed content items from data_flow.raw_content.

        T5: Only returns rows where clean_text IS NOT NULL (populated by parser CleanContentJob).
        Phase 1 user-sources: also skips rows where source_id IS NULL so posts_features
        NOT NULL invariant is preserved (parser should always set source_id).
        """
        stmt = (
            select(RawContentModel)
            .where(RawContentModel.processing_status == "COMPLETED")
            .where(RawContentModel.is_processed_by_rec == False)  # noqa: E712
            .where(RawContentModel.clean_text.isnot(None))
            .where(RawContentModel.source_id.isnot(None))
            .order_by(RawContentModel.received_at)
            .limit(batch_size)
        )
        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.scalars().all()
            return [_map_raw_to_entity(row) for row in rows]

    async def get_raw_by_id(self, post_id: UUID) -> Optional[ContentFeatures]:
        """Return a single raw_content row by ID, or None if not found."""
        stmt = select(RawContentModel).where(RawContentModel.id == post_id)
        async with self._session_factory() as session:
            result = await session.execute(stmt)
            row = result.scalar_one_or_none()
            return _map_raw_to_entity(row) if row is not None else None

    async def save_features(self, features: ContentFeatures) -> None:
        """Persist extracted NLP features and mark raw_content as processed."""
        embedding_value = features.embedding

        features_stmt = (
            insert(PostsFeaturesModel)
            .values(
                post_id=features.post_id,
                source_id=features.source_id,
                source_type=features.source_type,
                text_length=features.text_length,
                word_count=features.word_count,
                reading_time=features.reading_time,
                complexity=features.complexity,
                is_short_form=features.is_short_form,
                is_long_form=features.is_long_form,
                topic_1=features.topic_1,
                topic_1_score=features.topic_1_score,
                topic_2=features.topic_2,
                topic_2_score=features.topic_2_score,
                topic_3=features.topic_3,
                topic_3_score=features.topic_3_score,
                sentiment=features.sentiment,
                sentiment_score=features.sentiment_score,
                entities_persons=features.entities_persons,
                entities_organizations=features.entities_organizations,
                entities_locations=features.entities_locations,
                embedding=embedding_value,
                processed_at=features.processed_at or datetime.now(timezone.utc),
            )
            .on_conflict_do_update(
                index_elements=["post_id"],
                set_={
                    "source_id": features.source_id,
                    "source_type": features.source_type,
                    "text_length": features.text_length,
                    "word_count": features.word_count,
                    "reading_time": features.reading_time,
                    "complexity": features.complexity,
                    "is_short_form": features.is_short_form,
                    "is_long_form": features.is_long_form,
                    "topic_1": features.topic_1,
                    "topic_1_score": features.topic_1_score,
                    "topic_2": features.topic_2,
                    "topic_2_score": features.topic_2_score,
                    "topic_3": features.topic_3,
                    "topic_3_score": features.topic_3_score,
                    "sentiment": features.sentiment,
                    "sentiment_score": features.sentiment_score,
                    "entities_persons": features.entities_persons,
                    "entities_organizations": features.entities_organizations,
                    "entities_locations": features.entities_locations,
                    "embedding": embedding_value,
                    "processed_at": features.processed_at or datetime.now(timezone.utc),
                },
            )
        )

        flag_stmt = (
            update(RawContentModel)
            .where(RawContentModel.id == features.post_id)
            .values(is_processed_by_rec=True)
        )

        async with self._session_factory() as session:
            await session.execute(features_stmt)
            await session.execute(flag_stmt)
            await session.commit()

    async def get_candidates_by_freshness(
        self,
        max_age_hours: int,
        limit: int,
        exclude_source_ids: Optional[List[UUID]] = None,
    ) -> List[ContentFeatures]:
        """Return candidate content items newer than max_age_hours, up to limit.

        Only returns posts that have a corresponding published_content row, so the
        feed pipeline never selects unpublished items that cannot be mapped to a
        frontend-visible content ID.

        Phase 1 user-sources: optional exclude_source_ids filters out posts whose
        source_id matches any in the list.
        """
        cutoff = datetime.now(tz=timezone.utc) - timedelta(hours=max_age_hours)

        stmt = (
            select(PostsFeaturesModel)
            .join(
                PublishedContentModel,
                PostsFeaturesModel.post_id == PublishedContentModel.content_id,
            )
            .where(PostsFeaturesModel.processed_at >= cutoff)
        )
        if exclude_source_ids:
            stmt = stmt.where(
                PostsFeaturesModel.source_id.notin_(exclude_source_ids)
            )
        stmt = stmt.order_by(PostsFeaturesModel.processed_at.desc()).limit(limit)

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.scalars().all()
            return [_map_features_row(row) for row in rows]

    async def get_candidates_by_embedding(
        self,
        embedding: List[float],
        limit: int,
        exclude_source_ids: Optional[List[UUID]] = None,
    ) -> List[ContentFeatures]:
        """Return candidate content items closest to the given embedding (cosine distance).

        Only returns posts with a published_content mapping.
        Phase 1 user-sources: optional exclude_source_ids applied before HNSW ordering.
        """
        stmt = (
            select(PostsFeaturesModel)
            .join(
                PublishedContentModel,
                PostsFeaturesModel.post_id == PublishedContentModel.content_id,
            )
            .where(PostsFeaturesModel.embedding.isnot(None))
        )
        if exclude_source_ids:
            stmt = stmt.where(
                PostsFeaturesModel.source_id.notin_(exclude_source_ids)
            )
        stmt = stmt.order_by(PostsFeaturesModel.embedding.op("<=>")(embedding)).limit(limit)

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.scalars().all()
            return [_map_features_row(row) for row in rows]

    async def get_candidates_by_sources(
        self,
        include_source_ids: List[UUID],
        exclude_source_ids: Optional[List[UUID]],
        max_age_hours: int,
        limit: int,
    ) -> List[ContentFeatures]:
        """Return candidates restricted to include_source_ids, ordered by freshness.

        Phase 1 user-sources narrow path: single source-scoped candidate query
        used when the caller passes an explicit include list. Excludes posts
        whose source_id is in exclude_source_ids (exclude wins over include).

        Only returns posts that have a published_content mapping.
        Returns [] without a DB round-trip if include_source_ids is empty.
        """
        if not include_source_ids:
            return []

        cutoff = datetime.now(tz=timezone.utc) - timedelta(hours=max_age_hours)

        stmt = (
            select(PostsFeaturesModel)
            .join(
                PublishedContentModel,
                PostsFeaturesModel.post_id == PublishedContentModel.content_id,
            )
            .where(PostsFeaturesModel.processed_at >= cutoff)
            .where(PostsFeaturesModel.source_id.in_(include_source_ids))
        )
        if exclude_source_ids:
            stmt = stmt.where(
                PostsFeaturesModel.source_id.notin_(exclude_source_ids)
            )
        stmt = stmt.order_by(PostsFeaturesModel.processed_at.desc()).limit(limit)

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.scalars().all()
            return [_map_features_row(row) for row in rows]

    async def get_by_ids(self, post_ids: list[UUID]) -> List[ContentFeatures]:
        """Return content features for the given post_ids."""
        if not post_ids:
            return []

        stmt = select(PostsFeaturesModel).where(
            PostsFeaturesModel.post_id.in_(post_ids)
        )

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.scalars().all()
            return [_map_features_row(row) for row in rows]

    async def get_dedup_clusters(self, post_ids: list[UUID]) -> dict[UUID, int]:
        """Return post_id -> cluster_id using EXACT/DUPLICATE similarity edges."""
        if not post_ids:
            return {}

        async with self._session_factory() as session:
            art_stmt = select(ArticleModel).where(
                ArticleModel.raw_content_id.in_(post_ids)
            )
            art_result = await session.execute(art_stmt)
            articles = art_result.scalars().all()

            article_id_to_post: dict[int, UUID] = {
                a.id: a.raw_content_id for a in articles
            }
            article_ids = list(article_id_to_post.keys())

            if not article_ids:
                return {pid: i for i, pid in enumerate(post_ids)}

            sim_stmt = select(SimilarityModel).where(
                SimilarityModel.rel_type.in_(["EXACT", "DUPLICATE"]),
                SimilarityModel.article_a.in_(article_ids),
                SimilarityModel.article_b.in_(article_ids),
            )
            sim_result = await session.execute(sim_stmt)
            similarities = sim_result.scalars().all()

        uf = _UnionFind()
        for sim in similarities:
            uf.union(sim.article_a, sim.article_b)

        root_to_cluster: dict[int, int] = {}
        cluster_counter = 0
        result: dict[UUID, int] = {}

        for art_id, post_id in article_id_to_post.items():
            root = uf.find(art_id)
            if root not in root_to_cluster:
                root_to_cluster[root] = cluster_counter
                cluster_counter += 1
            result[post_id] = root_to_cluster[root]

        # Posts with no article mapping get unique cluster IDs
        for pid in post_ids:
            if pid not in result:
                result[pid] = cluster_counter
                cluster_counter += 1

        return result

    async def get_related_pairs(self, post_ids: list[UUID]) -> dict[UUID, set[UUID]]:
        """Return symmetric adjacency map for RELATED posts among candidates."""
        if not post_ids:
            return {}

        async with self._session_factory() as session:
            art_stmt = select(ArticleModel).where(
                ArticleModel.raw_content_id.in_(post_ids)
            )
            art_result = await session.execute(art_stmt)
            articles = art_result.scalars().all()

            article_id_to_post: dict[int, UUID] = {
                a.id: a.raw_content_id for a in articles
            }
            article_ids = list(article_id_to_post.keys())

            if not article_ids:
                return {}

            sim_stmt = select(SimilarityModel).where(
                SimilarityModel.rel_type == "RELATED",
                SimilarityModel.article_a.in_(article_ids),
                SimilarityModel.article_b.in_(article_ids),
            )
            sim_result = await session.execute(sim_stmt)
            similarities = sim_result.scalars().all()

        adjacency: dict[UUID, set[UUID]] = {}
        for sim in similarities:
            post_a = article_id_to_post.get(sim.article_a)
            post_b = article_id_to_post.get(sim.article_b)
            if post_a is None or post_b is None:
                continue
            adjacency.setdefault(post_a, set()).add(post_b)
            adjacency.setdefault(post_b, set()).add(post_a)

        return adjacency

    async def get_published_ids(self, content_ids: list[UUID]) -> dict[UUID, UUID]:
        """Return raw_content.id -> published_content.id mapping."""
        if not content_ids:
            return {}

        stmt = select(PublishedContentModel).where(
            PublishedContentModel.content_id.in_(content_ids)
        )

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.scalars().all()
            return {row.content_id: row.id for row in rows}

    async def get_raw_id_by_published_id(self, published_id: UUID) -> Optional[UUID]:
        """Return posts_features.post_id (= raw_content.id) for a given published_content.id."""
        stmt = select(PublishedContentModel.content_id).where(
            PublishedContentModel.id == published_id
        )
        async with self._session_factory() as session:
            result = await session.execute(stmt)
            return result.scalar_one_or_none()

    async def get_recent_published_ids(self, limit: int) -> list[UUID]:
        """Return the most recently published content IDs, ordered by published_at DESC."""
        stmt = (
            select(PublishedContentModel.id)
            .order_by(PublishedContentModel.published_at.desc())
            .limit(limit)
        )

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            return [row[0] for row in result.all()]

    async def get_corpus_sample(self, limit: int = 1000) -> list[ContentFeatures]:
        """Return up to limit posts from posts_features for eval corpus sampling."""
        stmt = (
            select(PostsFeaturesModel)
            .order_by(PostsFeaturesModel.processed_at.desc().nullslast())
            .limit(limit)
        )

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.scalars().all()
            return [_map_features_row(row) for row in rows]

    async def get_source_post_counts(self) -> dict[UUID, int]:
        """Return source_id -> post count map from posts_features. Eval-only."""
        stmt = select(
            PostsFeaturesModel.source_id,
            func.count().label("cnt"),
        ).group_by(PostsFeaturesModel.source_id)

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.all()
        return {row.source_id: row.cnt for row in rows}
