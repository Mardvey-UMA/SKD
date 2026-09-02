"""ProcessContentUseCase application use case."""
from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timezone
from uuid import UUID

from src.application.dto.process_content import ProcessContentCommand, ProcessContentResult

logger = logging.getLogger(__name__)
from src.domain.entities.content_features import ContentFeatures
from src.domain.interfaces.content_repository import ContentRepository
from src.domain.interfaces.topic_classifier import TopicClassifier
from src.domain.interfaces.sentiment_analyzer import SentimentAnalyzer
from src.domain.interfaces.entity_extractor import EntityExtractor
from src.domain.interfaces.content_encoder import ContentEncoder
from src.domain.interfaces.text_analyzer import TextAnalyzer
from src.infrastructure.nlp.text_window import extract_hmt, prepend_title


class ProcessContentUseCase:
    """Processes unprocessed posts through the NLP pipeline.

    Fetches unprocessed posts from the content repository, runs each through
    all 5 NLP operations (topics, sentiment, NER, embeddings, text metrics),
    creates ContentFeatures with the results, and saves them.
    Implements design/02-content-pipeline.md.

    T5: Uses token-aware H+M+T truncation via encoder.tokenizer instead of
    the naive text[:2000] char-based hack. All limits are injected via
    constructor (no magic numbers in this file).
    """

    def __init__(
        self,
        content_repo: ContentRepository,
        topic_classifier: TopicClassifier,
        sentiment_analyzer: SentimentAnalyzer,
        entity_extractor: EntityExtractor,
        content_encoder: ContentEncoder,
        text_analyzer: TextAnalyzer,
        max_nlp_tokens: int = 512,
        max_text_bytes: int = 1_000_000,
        truncate_text_bytes: int = 500_000,
    ) -> None:
        self._content_repo = content_repo
        self._topic_classifier = topic_classifier
        self._sentiment_analyzer = sentiment_analyzer
        self._entity_extractor = entity_extractor
        self._content_encoder = content_encoder
        self._text_analyzer = text_analyzer
        self._max_nlp_tokens = max_nlp_tokens
        self._max_text_bytes = max_text_bytes
        self._truncate_text_bytes = truncate_text_bytes

    async def process_single_by_id(self, post_id: UUID) -> bool:
        """Load a single raw_content row by ID and run the full NLP pipeline.

        Returns True if the post was found and processed, False if not found.
        """
        post = await self._content_repo.get_raw_by_id(post_id)
        if post is None:
            return False
        features = await self._process_single(post)
        await self._content_repo.save_features(features)
        return True

    async def execute(self, command: ProcessContentCommand) -> ProcessContentResult:
        """Fetch unprocessed posts and run them through the full NLP pipeline.

        Args:
            command: ProcessContentCommand specifying batch_size

        Returns:
            ProcessContentResult with count of processed posts
        """
        posts = await self._content_repo.get_unprocessed(command.batch_size)

        if not posts:
            return ProcessContentResult(processed_count=0)

        processed_count = 0
        per_post_timeout = 60  # seconds — skip post if NLP takes longer
        for post in posts:
            try:
                async with asyncio.timeout(per_post_timeout):
                    features = await self._process_single(post)
            except TimeoutError:
                logger.warning(
                    "Timeout (%ds) processing post %s, creating fallback features",
                    per_post_timeout,
                    post.post_id,
                )
                features = ContentFeatures(
                    post_id=post.post_id,
                    content=post.content,
                    post_date=post.post_date,
                    title=post.title,
                    source_id=post.source_id,
                    source_type=post.source_type,
                    processed_at=datetime.now(timezone.utc),
                )
            except Exception:
                logger.exception("Error processing post %s, creating fallback features", post.post_id)
                features = ContentFeatures(
                    post_id=post.post_id,
                    content=post.content,
                    post_date=post.post_date,
                    title=post.title,
                    source_id=post.source_id,
                    source_type=post.source_type,
                    processed_at=datetime.now(timezone.utc),
                )
            await self._content_repo.save_features(features)
            processed_count += 1

        return ProcessContentResult(processed_count=processed_count)

    async def _process_single(self, post: ContentFeatures) -> ContentFeatures:
        """Run all NLP operations on a single post and return enriched ContentFeatures."""
        title = post.title or ""
        body = post.content or ""

        # Byte guard: very large texts are truncated before tokenization
        if len(body.encode("utf-8")) > self._max_text_bytes:
            logger.warning(
                "clean_text exceeds %d bytes for post %s, truncating to %d bytes",
                self._max_text_bytes,
                post.post_id,
                self._truncate_text_bytes,
            )
            body = body[: self._truncate_text_bytes]

        full_text = prepend_title(title, body)
        full_text = full_text.strip()

        if not full_text:
            text_metrics = self._text_analyzer.analyze("")
            return ContentFeatures(
                post_id=post.post_id,
                content=post.content,
                post_date=post.post_date,
                title=post.title,
                source_id=post.source_id,
                source_type=post.source_type,
                text_length=text_metrics.get("text_length"),
                word_count=text_metrics.get("word_count"),
                reading_time=text_metrics.get("reading_time_min"),
                complexity=text_metrics.get("complexity"),
                is_short_form=text_metrics.get("is_short_form"),
                is_long_form=text_metrics.get("is_long_form"),
                topic_1=None, topic_1_score=None,
                topic_2=None, topic_2_score=None,
                topic_3=None, topic_3_score=None,
                sentiment=None, sentiment_score=None,
                entities_persons=[], entities_organizations=[], entities_locations=[],
                embedding=None,
                processed_at=datetime.now(timezone.utc),
            )

        # Token-aware H+M+T truncation (T5: replaces text[:2000] hack)
        tokenizer = self._content_encoder.tokenizer
        nlp_text, was_truncated = extract_hmt(full_text, tokenizer, self._max_nlp_tokens)
        if was_truncated:
            logger.info(
                "H+M+T applied to post %s (max_tokens=%d)",
                post.post_id,
                self._max_nlp_tokens,
            )

        # Run all 5 NLP operations
        topics = await self._topic_classifier.classify(nlp_text)
        sentiment_label, sentiment_score = await self._sentiment_analyzer.analyze(nlp_text)
        # spaCy NER continues to use full body (4096 char internal limit unchanged)
        entities = await self._entity_extractor.extract(body)
        embedding = await self._content_encoder.encode(nlp_text)
        text_metrics = self._text_analyzer.analyze(body)  # text metrics on full body

        # Extract top-3 topics
        topic_1 = topics[0][0] if len(topics) > 0 else None
        topic_1_score = topics[0][1] if len(topics) > 0 else None
        topic_2 = topics[1][0] if len(topics) > 1 else None
        topic_2_score = topics[1][1] if len(topics) > 1 else None
        topic_3 = topics[2][0] if len(topics) > 2 else None
        topic_3_score = topics[2][1] if len(topics) > 2 else None

        return ContentFeatures(
            post_id=post.post_id,
            content=post.content,
            post_date=post.post_date,
            title=post.title,
            source_id=post.source_id,
            source_type=post.source_type,
            text_length=text_metrics.get("text_length"),
            word_count=text_metrics.get("word_count"),
            reading_time=text_metrics.get("reading_time_min"),
            complexity=text_metrics.get("complexity"),
            is_short_form=text_metrics.get("is_short_form"),
            is_long_form=text_metrics.get("is_long_form"),
            topic_1=topic_1,
            topic_1_score=topic_1_score,
            topic_2=topic_2,
            topic_2_score=topic_2_score,
            topic_3=topic_3,
            topic_3_score=topic_3_score,
            sentiment=sentiment_label,
            sentiment_score=sentiment_score,
            entities_persons=entities.get("persons", []),
            entities_organizations=entities.get("organizations", []),
            entities_locations=entities.get("locations", []),
            embedding=embedding,
            processed_at=datetime.now(timezone.utc),
        )
