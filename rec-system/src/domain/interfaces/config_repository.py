from __future__ import annotations

from abc import ABC, abstractmethod


class ConfigRepository(ABC):
    """Abstract repository for reading configuration values."""

    @abstractmethod
    async def get_config(self, key: str) -> dict:
        """Return configuration dict for the given key."""
        ...
