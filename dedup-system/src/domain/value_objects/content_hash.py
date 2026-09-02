import re
from dataclasses import dataclass


@dataclass(frozen=True)
class ContentHash:
    value: str

    def __post_init__(self) -> None:
        if len(self.value) != 64:
            raise ValueError(f"ContentHash must be 64 characters, got {len(self.value)}")
        if not re.fullmatch(r"[0-9a-f]+", self.value):
            raise ValueError("ContentHash must contain only hex characters")

    def __str__(self) -> str:
        return self.value
