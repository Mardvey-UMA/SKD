from src.domain.entities.similarity import Similarity
from src.domain.interfaces.similarity_repo_port import SimilarityRepositoryPort

_UPSERT_SQL = """
    INSERT INTO data_flow.similarities (article_a, article_b, score, rel_type)
    VALUES (%s, %s, %s, %s)
    ON CONFLICT (article_a, article_b)
    DO UPDATE SET score = GREATEST(data_flow.similarities.score, EXCLUDED.score),
                  rel_type = CASE
                      WHEN EXCLUDED.score > data_flow.similarities.score THEN EXCLUDED.rel_type
                      ELSE data_flow.similarities.rel_type
                  END
"""


class Psycopg2SimilarityRepository(SimilarityRepositoryPort):
    def __init__(self, conn) -> None:
        self._conn = conn

    def save_batch(self, similarities: list[Similarity]) -> None:
        if not similarities:
            return
        cursor = self._conn.cursor()
        for sim in similarities:
            cursor.execute(
                _UPSERT_SQL,
                (sim.article_a, sim.article_b, sim.score, sim.rel_type.value),
            )
        self._conn.commit()

    def save_one(self, similarity: Similarity) -> None:
        cursor = self._conn.cursor()
        cursor.execute(
            _UPSERT_SQL,
            (
                similarity.article_a,
                similarity.article_b,
                similarity.score,
                similarity.rel_type.value,
            ),
        )
        self._conn.commit()
