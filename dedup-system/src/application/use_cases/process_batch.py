import logging
from typing import Optional
from uuid import UUID

import numpy as np

from src.application.dto.batch_result_dto import BatchResultDTO
from src.domain.entities.article import Article
from src.domain.entities.raw_content import RawContent
from src.domain.entities.similarity import Similarity
from src.domain.interfaces.article_repo_port import ArticleRepositoryPort
from src.domain.interfaces.config_port import ConfigPort
from src.domain.interfaces.encoder_port import EncoderPort
from src.domain.interfaces.raw_content_repo_port import RawContentRepositoryPort
from src.domain.interfaces.similarity_repo_port import SimilarityRepositoryPort
from src.domain.services.classifier import ThresholdClassifier
from src.domain.services.text_normalizer import TextNormalizer
from src.domain.services.text_window import prepend_title
from src.domain.value_objects.content_hash import ContentHash
from src.domain.value_objects.embedding import Embedding

logger = logging.getLogger(__name__)


class ProcessBatchUseCase:
    def __init__(
        self,
        encoder: EncoderPort,
        article_repo: ArticleRepositoryPort,
        similarity_repo: SimilarityRepositoryPort,
        raw_content_repo: RawContentRepositoryPort,
        config_port: ConfigPort,
    ) -> None:
        self._encoder = encoder
        self._article_repo = article_repo
        self._similarity_repo = similarity_repo
        self._raw_content_repo = raw_content_repo
        self._config_port = config_port
        self._normalizer = TextNormalizer()
        th_dup, th_rel = config_port.load_thresholds()
        self._classifier = ThresholdClassifier(th_dup=th_dup, th_rel=th_rel)

    def execute(self, batch: list[RawContent]) -> BatchResultDTO:
        if not batch:
            return BatchResultDTO(
                total_processed=0, exact_duplicates=0,
                new_articles=0, relationships_created=0, errors=0,
            )

        th_dup, th_rel = self._config_port.load_thresholds()
        self._classifier.update_thresholds(th_dup, th_rel)
        top_k, window_hours = self._config_port.load_search_params()
        _, encoding_batch_size = self._config_port.load_batch_params()

        exact_dups = 0
        new_count = 0
        rel_count = 0
        errors = 0

        # Stage 1: Normalize and hash
        normalized: list[tuple[RawContent, str, ContentHash]] = []
        for rc in batch:
            norm_text = self._normalizer.normalize(rc.text_for_normalization)
            content_hash = self._normalizer.compute_hash(norm_text)
            normalized.append((rc, norm_text, content_hash))

        # Stage 2: Check exact duplicates
        all_hashes = [h for _, _, h in normalized]
        existing = self._article_repo.find_by_hashes(all_hashes)

        new_items: list[tuple[RawContent, str, ContentHash]] = []
        exact_dup_items: list[tuple[RawContent, str, ContentHash, int]] = []
        seen_in_batch: dict[str, int] = {}

        for rc, norm_text, content_hash in normalized:
            hash_str = str(content_hash)
            if hash_str in existing:
                exact_dup_items.append((rc, norm_text, content_hash, existing[hash_str]))
            elif hash_str in seen_in_batch:
                exact_dup_items.append((rc, norm_text, content_hash, seen_in_batch[hash_str]))
            else:
                new_items.append((rc, norm_text, content_hash))
                seen_in_batch[hash_str] = -1  # placeholder, updated after save

        # Handle exact duplicates
        done_ids: list[UUID] = []

        for rc, norm_text, content_hash, original_id in exact_dup_items:
            try:
                dup_article = Article(
                    id=None, raw_content_id=rc.id,
                    content_hash=content_hash,
                    normalized_text=norm_text,
                    embedding=None, source=rc.source_type, created_at=None,
                )
                dup_id = self._article_repo.save_article(dup_article)
                if dup_id == original_id:
                    logger.info("Skipping self-similarity for rc_id=%s (article_id=%s)", rc.id, dup_id)
                    done_ids.append(rc.id)
                    continue
                if original_id > 0:
                    self._similarity_repo.save_one(
                        Similarity.create_exact(dup_id, original_id)
                    )
                exact_dups += 1
                done_ids.append(rc.id)
            except Exception:
                logger.exception("Error processing exact duplicate rc_id=%s", rc.id)
                errors += 1
                done_ids.append(rc.id)

        if not new_items:
            if done_ids:
                self._raw_content_repo.mark_processed(done_ids)
            return BatchResultDTO(
                total_processed=len(batch), exact_duplicates=exact_dups,
                new_articles=0, relationships_created=rel_count, errors=errors,
            )

        # Stage 3: Encode new articles
        try:
            texts_to_encode = [
                prepend_title(rc.title, rc.content_body) for rc, _, _ in new_items
            ]
            embeddings = self._encoder.encode_batch(
                texts_to_encode, batch_size=encoding_batch_size
            )
        except Exception:
            logger.exception("Encoding failed for batch")
            errors += len(new_items)
            if done_ids:
                self._raw_content_repo.mark_processed(done_ids)
            return BatchResultDTO(
                total_processed=len(batch), exact_duplicates=exact_dups,
                new_articles=0, relationships_created=0, errors=errors,
            )

        # Stage 4-6: Save, search, classify, write graph
        all_new_ids: list[int] = []
        all_scores: list[list[float]] = []
        all_neighbor_ids: list[list[int]] = []

        for i, (rc, norm_text, content_hash) in enumerate(new_items):
            try:
                emb = Embedding(embeddings[i])
            except ValueError:
                logger.warning("Skipping post rc_id=%s: invalid embedding (NaN/Inf)", rc.id)
                errors += 1
                done_ids.append(rc.id)
                continue
            try:
                article = Article(
                    id=None, raw_content_id=rc.id,
                    content_hash=content_hash,
                    normalized_text=norm_text,
                    embedding=emb, source=rc.source_type, created_at=None,
                )
                article_id = self._article_repo.save_article(article)
                all_new_ids.append(article_id)

                # Update seen_in_batch for in-batch dup detection
                seen_in_batch[str(content_hash)] = article_id

                neighbors = self._article_repo.search_neighbors(
                    embeddings[i], top_k=top_k, window_hours=window_hours
                )
                scores = [s for _, s in neighbors]
                neighbor_ids = [nid for nid, _ in neighbors]
                all_scores.append(scores)
                all_neighbor_ids.append(neighbor_ids)

                new_count += 1
                done_ids.append(rc.id)
            except Exception:
                logger.exception("Error processing rc_id=%s", rc.id)
                errors += 1
                done_ids.append(rc.id)

        # Stage 5-6: Classify and save relationships
        if all_new_ids:
            relationships = self._classifier.classify_relationships(
                all_new_ids, all_scores, all_neighbor_ids
            )
            if relationships:
                self._similarity_repo.save_batch(relationships)
                rel_count = len(relationships)

        if done_ids:
            self._raw_content_repo.mark_processed(done_ids)

        return BatchResultDTO(
            total_processed=len(batch),
            exact_duplicates=exact_dups,
            new_articles=new_count,
            relationships_created=rel_count,
            errors=errors,
        )
