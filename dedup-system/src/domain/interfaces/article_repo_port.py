from abc import ABC, abstractmethod
from typing import Optional

import numpy as np

from src.domain.entities.article import Article
from src.domain.value_objects.content_hash import ContentHash


class ArticleRepositoryPort(ABC):
    @abstractmethod
    def save_article(self, article: Article) -> int:
        ...

    @abstractmethod
    def find_by_hashes(self, hashes: list[ContentHash]) -> dict[str, int]:
        ...

    @abstractmethod
    def search_neighbors(
        self, embedding: np.ndarray, top_k: int, window_hours: int
    ) -> list[tuple[int, float]]:
        ...

    @abstractmethod
    def find_by_id(self, article_id: int) -> Optional[Article]:
        ...
