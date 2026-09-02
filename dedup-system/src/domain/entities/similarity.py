from dataclasses import dataclass
from datetime import datetime
from typing import Optional

from src.domain.value_objects.rel_type import RelationType


@dataclass
class Similarity:
    article_a: int
    article_b: int
    score: float
    rel_type: RelationType
    created_at: Optional[datetime] = None

    @classmethod
    def create(
        cls,
        article_id_1: int,
        article_id_2: int,
        score: float,
        rel_type: RelationType,
    ) -> "Similarity":
        if article_id_1 == article_id_2:
            raise ValueError("Cannot create similarity between article and itself")
        if not (0 <= score <= 1):
            raise ValueError(f"Score must be between 0 and 1, got {score}")
        a, b = min(article_id_1, article_id_2), max(article_id_1, article_id_2)
        return cls(article_a=a, article_b=b, score=score, rel_type=rel_type)

    @classmethod
    def create_exact(cls, article_id_1: int, article_id_2: int) -> "Similarity":
        return cls.create(
            article_id_1=article_id_1,
            article_id_2=article_id_2,
            score=1.0,
            rel_type=RelationType.EXACT,
        )
