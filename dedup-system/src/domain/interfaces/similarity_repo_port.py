from abc import ABC, abstractmethod

from src.domain.entities.similarity import Similarity


class SimilarityRepositoryPort(ABC):
    @abstractmethod
    def save_batch(self, similarities: list[Similarity]) -> None:
        ...

    @abstractmethod
    def save_one(self, similarity: Similarity) -> None:
        ...
