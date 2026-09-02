from __future__ import annotations

from dataclasses import dataclass


@dataclass
class ProcessContentCommand:
    batch_size: int


@dataclass
class ProcessContentResult:
    processed_count: int
