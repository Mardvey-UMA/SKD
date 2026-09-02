from abc import ABC, abstractmethod


class ConfigPort(ABC):
    @abstractmethod
    def load_thresholds(self) -> tuple[float, float]:
        ...

    @abstractmethod
    def load_search_params(self) -> tuple[int, int]:
        ...

    @abstractmethod
    def load_batch_params(self) -> tuple[int, int]:
        ...
