import logging
import os
import signal
import time

import psycopg2

from src.application.use_cases.process_batch import ProcessBatchUseCase
from src.config import Settings
from src.domain.interfaces.config_port import ConfigPort
from src.domain.interfaces.raw_content_repo_port import RawContentRepositoryPort
from src.infrastructure.nlp.bge_m3_encoder import BgeM3Encoder
from src.infrastructure.persistence.psycopg2_article_repo import (
    Psycopg2ArticleRepository,
)
from src.infrastructure.persistence.psycopg2_config_repo import (
    Psycopg2ConfigRepository,
)
from src.infrastructure.persistence.psycopg2_raw_content_repo import (
    Psycopg2RawContentRepository,
)
from src.infrastructure.persistence.psycopg2_similarity_repo import (
    Psycopg2SimilarityRepository,
)

logger = logging.getLogger(__name__)


class WorkerRunner:
    def __init__(
        self,
        raw_content_repo: RawContentRepositoryPort,
        process_batch_use_case: ProcessBatchUseCase,
        config_port: ConfigPort,
        poll_interval: float = 0.2,
    ) -> None:
        self._raw_content_repo = raw_content_repo
        self._process_batch = process_batch_use_case
        self._config_port = config_port
        self._poll_interval = poll_interval
        self._running = True
        self._conn = raw_content_repo._conn

    def process_one_cycle(self) -> bool:
        batch_size, _ = self._config_port.load_batch_params()
        logger.debug("Polling for pending batch (size=%d)...", batch_size)
        batch = self._raw_content_repo.fetch_pending_batch(batch_size=batch_size)
        if not batch:
            return False
        logger.info("Fetched %d rows for processing", len(batch))
        result = self._process_batch.execute(batch)
        logger.info("Batch result: %s", result.summary())
        return True

    def run(self) -> None:
        signal.signal(signal.SIGINT, self._shutdown)
        signal.signal(signal.SIGTERM, self._shutdown)
        logger.info("Worker started, poll_interval=%.1fs", self._poll_interval)
        idle_count = 0
        while self._running:
            try:
                had_work = self.process_one_cycle()
                if not had_work:
                    idle_count += 1
                    if idle_count % 50 == 1:
                        logger.info("No pending rows, idle (poll #%d)", idle_count)
                    time.sleep(self._poll_interval)
                else:
                    idle_count = 0
            except Exception:
                logger.exception("Error in worker cycle, rolling back transaction")
                try:
                    self._conn.rollback()
                except Exception:
                    logger.warning("Rollback failed, reconnecting...")
                    try:
                        self._conn.close()
                    except Exception:
                        pass
                    self._conn = psycopg2.connect(os.environ["DEDUP_DB_DSN"])
                    self._raw_content_repo._conn = self._conn
                time.sleep(2.0)
        logger.info("Worker stopped")

    def _shutdown(self, signum, frame) -> None:
        logger.info("Shutdown signal received")
        self._running = False

    @classmethod
    def from_env(cls) -> "WorkerRunner":
        settings = Settings()
        logger.info(
            "Connecting to DB: %s",
            settings.dedup_db_dsn.split("@")[1] if "@" in settings.dedup_db_dsn else "***",
        )
        conn = psycopg2.connect(settings.dedup_db_dsn)
        raw_repo = Psycopg2RawContentRepository(conn=conn)
        article_repo = Psycopg2ArticleRepository(conn=conn)
        sim_repo = Psycopg2SimilarityRepository(conn=conn)
        config_repo = Psycopg2ConfigRepository(conn=conn)
        logger.info("Loading model: %s", settings.dedup_model_name)
        encoder = BgeM3Encoder(
            model_name=settings.dedup_model_name,
            max_tokens=settings.dedup_max_tokens,
            max_text_bytes=settings.dedup_max_clean_text_bytes,
            truncate_text_bytes=settings.dedup_truncate_text_bytes,
        )
        logger.info("Model loaded successfully")
        use_case = ProcessBatchUseCase(
            encoder=encoder,
            article_repo=article_repo,
            similarity_repo=sim_repo,
            raw_content_repo=raw_repo,
            config_port=config_repo,
        )
        return cls(
            raw_content_repo=raw_repo,
            process_batch_use_case=use_case,
            config_port=config_repo,
            poll_interval=settings.dedup_poll_interval,
        )


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(name)s %(levelname)s %(message)s",
    )
    # Reduce noise from httpx/httpcore
    logging.getLogger("httpx").setLevel(logging.WARNING)
    logging.getLogger("httpcore").setLevel(logging.WARNING)
    logging.getLogger("sentence_transformers").setLevel(logging.WARNING)
    logging.getLogger("huggingface_hub").setLevel(logging.WARNING)

    runner = WorkerRunner.from_env()
    runner.run()
