import logging
from datetime import datetime
from uuid import UUID

from src.domain.entities.raw_content import RawContent
from src.domain.interfaces.raw_content_repo_port import RawContentRepositoryPort

logger = logging.getLogger(__name__)


class Psycopg2RawContentRepository(RawContentRepositoryPort):
    def __init__(self, conn) -> None:
        self._conn = conn

    def fetch_pending_batch(self, batch_size: int) -> list[RawContent]:
        cursor = self._conn.cursor()
        cursor.execute(
            """SELECT id,
                      raw_data->>'title' AS title,
                      clean_text,
                      source_type,
                      external_id,
                      raw_data->>'publishedAt' AS published_at
               FROM data_flow.raw_content
               WHERE processing_status = 'COMPLETED'
                 AND is_processed_by_dedup = false
                 AND clean_text IS NOT NULL
               ORDER BY received_at
               LIMIT %s
               FOR UPDATE SKIP LOCKED""",
            (batch_size,),
        )
        rows = cursor.fetchall()
        # Do NOT commit here — keep row locks until mark_processed() commits
        result = []
        for row in rows:
            published_at = None
            if row[5]:
                try:
                    published_at = datetime.fromisoformat(row[5].replace("Z", "+00:00"))
                except (ValueError, TypeError):
                    pass
            result.append(RawContent(
                id=row[0],
                title=row[1] or "",
                content_body=row[2] or "",
                source_type=row[3],
                external_id=row[4],
                published_at=published_at,
            ))
        logger.info("fetch_pending_batch: found %d rows (limit=%d)", len(result), batch_size)
        return result

    def mark_processed(self, ids: list[UUID]) -> None:
        if not ids:
            return
        logger.info("mark_processed: marking %d rows as processed", len(ids))
        cursor = self._conn.cursor()
        cursor.execute(
            "UPDATE data_flow.raw_content SET is_processed_by_dedup = true WHERE id = ANY(%s::uuid[])",
            ([str(uid) for uid in ids],),
        )
        self._conn.commit()
        logger.info("mark_processed: committed successfully")
