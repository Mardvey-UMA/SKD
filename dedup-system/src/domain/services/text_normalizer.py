import hashlib
import re
import unicodedata

from src.domain.value_objects.content_hash import ContentHash


class TextNormalizer:
    def normalize(self, raw_text: str) -> str:
        text = str(raw_text)
        text = "".join(
            ch for ch in text
            if unicodedata.category(ch)[0] != "C" or ch in "\n\t"
        )
        text = unicodedata.normalize("NFC", text)
        text = re.sub(r"\s+", " ", text).strip()
        return text

    def compute_hash(self, normalized_text: str) -> ContentHash:
        digest = hashlib.sha256(normalized_text.lower().encode("utf-8")).hexdigest()
        return ContentHash(digest)
