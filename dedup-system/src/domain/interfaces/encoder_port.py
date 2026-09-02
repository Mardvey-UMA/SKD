from abc import ABC, abstractmethod

import numpy as np


class EncoderPort(ABC):
    @abstractmethod
    def encode_batch(self, texts: list[str], batch_size: int = 32) -> np.ndarray:
        ...
