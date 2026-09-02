import numpy as np

EMBEDDING_DIM = 1024


class Embedding:
    def __init__(self, vector: np.ndarray, expected_dim: int = EMBEDDING_DIM) -> None:
        if vector.shape != (expected_dim,):
            raise ValueError(
                f"Expected embedding of dimension {expected_dim}, got shape {vector.shape}"
            )
        if np.any(np.isnan(vector)) or np.any(np.isinf(vector)):
            raise ValueError("Embedding contains NaN or Inf values")
        self._vector = vector
        self._expected_dim = expected_dim

    @property
    def dimension(self) -> int:
        return self._expected_dim

    @property
    def vector(self) -> np.ndarray:
        return self._vector

    def to_list(self) -> list[float]:
        return self._vector.tolist()
