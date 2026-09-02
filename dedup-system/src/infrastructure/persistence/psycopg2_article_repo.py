from typing import Optional
from uuid import UUID

import numpy as np
import psycopg2

from src.domain.entities.article import Article
from src.domain.interfaces.article_repo_port import ArticleRepositoryPort
from src.domain.value_objects.content_hash import ContentHash
from src.domain.value_objects.embedding import Embedding


class Psycopg2ArticleRepository(ArticleRepositoryPort):
    def __init__(self, conn) -> None:
        self._conn = conn

    def save_article(self, article: Article) -> int:
        cursor = self._conn.cursor()
        embedding_list = article.embedding.to_list() if article.embedding else None
        embedding_param = str(embedding_list) if embedding_list else None
        cursor.execute("SAVEPOINT sp_save_article")
        try:
            if embedding_param:
                cursor.execute(
                    """INSERT INTO data_flow.articles (raw_content_id, content_hash, normalized_text, embedding, source)
                       VALUES (%s::uuid, %s, %s, %s::vector, %s)
                       ON CONFLICT (raw_content_id) DO UPDATE SET content_hash = EXCLUDED.content_hash
                       RETURNING id""",
                    (
                        str(article.raw_content_id),
                        str(article.content_hash),
                        article.normalized_text,
                        embedding_param,
                        article.source,
                    ),
                )
            else:
                cursor.execute(
                    """INSERT INTO data_flow.articles (raw_content_id, content_hash, normalized_text, embedding, source)
                       VALUES (%s::uuid, %s, %s, NULL, %s)
                       ON CONFLICT (raw_content_id) DO UPDATE SET content_hash = EXCLUDED.content_hash
                       RETURNING id""",
                    (
                        str(article.raw_content_id),
                        str(article.content_hash),
                        article.normalized_text,
                        article.source,
                    ),
                )
            row = cursor.fetchone()
            cursor.execute("RELEASE SAVEPOINT sp_save_article")
            self._conn.commit()
            return row[0]
        except psycopg2.Error:
            cursor.execute("ROLLBACK TO SAVEPOINT sp_save_article")
            raise

    def find_by_hashes(self, hashes: list[ContentHash]) -> dict[str, int]:
        if not hashes:
            return {}
        cursor = self._conn.cursor()
        hash_strs = [str(h) for h in hashes]
        cursor.execute(
            "SELECT id, content_hash FROM data_flow.articles WHERE content_hash = ANY(%s)",
            (hash_strs,),
        )
        rows = cursor.fetchall()
        return {row[1]: row[0] for row in rows}

    def search_neighbors(
        self, embedding: np.ndarray, top_k: int, window_hours: int
    ) -> list[tuple[int, float]]:
        cursor = self._conn.cursor()
        emb_list = embedding.tolist()
        cursor.execute(
            """SELECT id, 1 - (embedding <=> %s::vector) AS score
               FROM data_flow.articles
               WHERE embedding IS NOT NULL
                 AND created_at > now() - make_interval(hours => %s)
               ORDER BY embedding <=> %s::vector
               LIMIT %s""",
            (str(emb_list), window_hours, str(emb_list), top_k),
        )
        return cursor.fetchall()

    def find_by_id(self, article_id: int) -> Optional[Article]:
        cursor = self._conn.cursor()
        cursor.execute(
            """SELECT id, raw_content_id, content_hash, normalized_text,
                      embedding, source, created_at
               FROM data_flow.articles WHERE id = %s""",
            (article_id,),
        )
        row = cursor.fetchone()
        if not row:
            return None
        embedding = None
        if row[4] is not None:
            embedding = Embedding(np.array(row[4], dtype=np.float32))
        return Article(
            id=row[0],
            raw_content_id=row[1],
            content_hash=ContentHash(row[2]),
            normalized_text=row[3],
            embedding=embedding,
            source=row[5],
            created_at=row[6],
        )
