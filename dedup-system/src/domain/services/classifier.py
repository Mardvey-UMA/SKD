from typing import Optional

from src.domain.entities.similarity import Similarity
from src.domain.value_objects.rel_type import RelationType


class ThresholdClassifier:
    def __init__(self, th_dup: float, th_rel: float) -> None:
        self._th_dup = th_dup
        self._th_rel = th_rel

    def classify(self, score: float) -> Optional[RelationType]:
        return RelationType.from_score(score, th_dup=self._th_dup, th_rel=self._th_rel)

    def classify_relationships(
        self,
        new_article_ids: list[int],
        scores: list[list[float]],
        neighbor_ids: list[list[int]],
    ) -> list[Similarity]:
        results: list[Similarity] = []
        for article_id, art_scores, art_neighbors in zip(
            new_article_ids, scores, neighbor_ids
        ):
            for score, neighbor_id in zip(art_scores, art_neighbors):
                if neighbor_id == article_id:
                    continue
                rel_type = self.classify(score)
                if rel_type is None:
                    continue
                results.append(
                    Similarity.create(
                        article_id_1=article_id,
                        article_id_2=neighbor_id,
                        score=score,
                        rel_type=rel_type,
                    )
                )
        return results

    def update_thresholds(self, th_dup: float, th_rel: float) -> None:
        self._th_dup = th_dup
        self._th_rel = th_rel
