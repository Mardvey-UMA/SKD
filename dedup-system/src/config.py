"""Application settings loaded from environment variables."""
from __future__ import annotations

import os


class Settings:
    """Immutable settings bag populated from environment variables with defaults."""

    def __init__(self) -> None:
        self.dedup_db_dsn: str = os.environ["DEDUP_DB_DSN"]
        self.dedup_model_name: str = os.environ.get("DEDUP_MODEL_NAME", "BAAI/bge-m3")
        self.dedup_poll_interval: float = float(
            os.environ.get("DEDUP_POLL_INTERVAL", "0.2")
        )
        self.dedup_max_tokens: int = int(os.environ.get("DEDUP_MAX_TOKENS", "8192"))
        self.dedup_max_clean_text_bytes: int = int(
            os.environ.get("DEDUP_MAX_CLEAN_TEXT_BYTES", "1048576")
        )
        self.dedup_truncate_text_bytes: int = int(
            os.environ.get("DEDUP_TRUNCATE_TEXT_BYTES", "524288")
        )
