from __future__ import annotations

from typing import Dict


class FormatPrefs:
    """Immutable value object for format preferences (avg_length and avg_complexity)."""

    def __init__(self, avg_length: float, avg_complexity: float) -> None:
        self._avg_length = avg_length
        self._avg_complexity = avg_complexity

    @property
    def avg_length(self) -> float:
        return self._avg_length

    @property
    def avg_complexity(self) -> float:
        return self._avg_complexity

    @classmethod
    def create_default(cls) -> "FormatPrefs":
        return cls(avg_length=200.0, avg_complexity=0.5)

    def apply_ema_update(
        self, lr: float, post_length: float, post_complexity: float
    ) -> "FormatPrefs":
        """EMA update: new = (1 - lr) * old + lr * new_value. Returns new instance."""
        new_length = (1 - lr) * self._avg_length + lr * post_length
        new_complexity = (1 - lr) * self._avg_complexity + lr * post_complexity
        return FormatPrefs(avg_length=new_length, avg_complexity=new_complexity)

    def to_dict(self) -> Dict[str, float]:
        return {"avg_length": self._avg_length, "avg_complexity": self._avg_complexity}

    @classmethod
    def from_dict(cls, data: Dict[str, float]) -> "FormatPrefs":
        return cls(avg_length=data["avg_length"], avg_complexity=data["avg_complexity"])
