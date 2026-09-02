from src.domain.interfaces.config_port import ConfigPort

_DEFAULTS = {
    "threshold_duplicate": "0.85",
    "threshold_related": "0.70",
    "search_top_k": "20",
    "search_window_hours": "72",
    "batch_size": "64",
    "encoding_batch_size": "32",
}


class Psycopg2ConfigRepository(ConfigPort):
    def __init__(self, conn) -> None:
        self._conn = conn

    def _load_keys(self, keys: list[str]) -> dict[str, str]:
        cursor = self._conn.cursor()
        cursor.execute(
            "SELECT key, value FROM data_flow.dedup_config WHERE key = ANY(%s)",
            (keys,),
        )
        rows = cursor.fetchall()
        result = {k: _DEFAULTS[k] for k in keys}
        for key, value in rows:
            result[key] = value
        return result

    def load_thresholds(self) -> tuple[float, float]:
        data = self._load_keys(["threshold_duplicate", "threshold_related"])
        return float(data["threshold_duplicate"]), float(data["threshold_related"])

    def load_search_params(self) -> tuple[int, int]:
        data = self._load_keys(["search_top_k", "search_window_hours"])
        return int(data["search_top_k"]), int(data["search_window_hours"])

    def load_batch_params(self) -> tuple[int, int]:
        data = self._load_keys(["batch_size", "encoding_batch_size"])
        return int(data["batch_size"]), int(data["encoding_batch_size"])
