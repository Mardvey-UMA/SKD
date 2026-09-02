"""PostgreSQL implementation of ConfigRepository."""
from __future__ import annotations

from typing import Callable, Dict, Optional

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from src.domain.interfaces.config_repository import ConfigRepository
from src.infrastructure.persistence.models.rec_config import RecConfigModel


class PgConfigRepository(ConfigRepository):
    """PostgreSQL adapter for configuration values."""

    def __init__(self, session_factory: Callable[[], AsyncSession]) -> None:
        self._session_factory = session_factory

    async def get_config(self, key: str) -> Optional[Dict]:
        """Return configuration dict for the given key, or None if missing."""
        stmt = select(RecConfigModel).where(RecConfigModel.key == key)

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            model = result.scalar_one_or_none()
            if model is None:
                return None
            return model.value

    async def get_all_configs(self) -> Dict[str, Dict]:
        """Return all configuration key-value pairs as a dict."""
        stmt = select(RecConfigModel)

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.scalars().all()
            return {row.key: row.value for row in rows}
