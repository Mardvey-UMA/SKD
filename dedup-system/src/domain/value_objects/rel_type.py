from enum import Enum
from typing import Optional


class RelationType(str, Enum):
    EXACT = "EXACT"
    DUPLICATE = "DUPLICATE"
    RELATED = "RELATED"

    @staticmethod
    def from_score(score: float, th_dup: float, th_rel: float) -> Optional["RelationType"]:
        if score >= 1.0:
            return RelationType.EXACT
        if score >= th_dup:
            return RelationType.DUPLICATE
        if score >= th_rel:
            return RelationType.RELATED
        return None
