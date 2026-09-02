import pytest
from src.domain.services.text_window import extract_hmt, prepend_title


class FakeTokenizer:
    """Word-based tokenizer for tests — one word = one token."""

    def encode(self, text: str, add_special_tokens: bool = False) -> list[int]:
        if not text:
            return []
        return text.split()

    def decode(self, tokens: list, skip_special_tokens: bool = True) -> str:
        return " ".join(tokens) if isinstance(tokens, list) else tokens


@pytest.mark.unit
def test_short_text_unchanged():
    t = FakeTokenizer()
    text = "this is a short text"
    result, truncated = extract_hmt(text, t, max_tokens=100)
    assert result == text
    assert truncated is False


@pytest.mark.unit
def test_long_text_truncated():
    t = FakeTokenizer()
    text = " ".join(f"word{i}" for i in range(1000))
    result, truncated = extract_hmt(text, t, max_tokens=50)
    assert truncated is True
    assert "word0" in result        # head present
    assert "word999" in result      # tail present
    assert "[...]" in result        # separator present


@pytest.mark.unit
def test_prepend_title_combines():
    assert prepend_title("Hello", "world") == "Hello. world"
    assert prepend_title("Hello?", "world") == "Hello? world"
    assert prepend_title(None, "world") == "world"
    assert prepend_title("Hello", "") == "Hello"


@pytest.mark.unit
def test_prepend_title_with_period_and_exclamation():
    assert prepend_title("Breaking news!", "details here") == "Breaking news! details here"
    assert prepend_title("End.", "body") == "End. body"
    assert prepend_title("Title:", "content") == "Title: content"


@pytest.mark.unit
def test_idempotent():
    t = FakeTokenizer()
    text = " ".join(f"w{i}" for i in range(500))
    once, _ = extract_hmt(text, t, max_tokens=100)
    twice, _ = extract_hmt(once, t, max_tokens=100)
    assert once == twice


@pytest.mark.unit
def test_empty_text():
    t = FakeTokenizer()
    result, truncated = extract_hmt("", t, max_tokens=100)
    assert result == ""
    assert truncated is False


@pytest.mark.unit
def test_returns_tuple():
    t = FakeTokenizer()
    result = extract_hmt("hello world", t, max_tokens=100)
    assert isinstance(result, tuple)
    assert len(result) == 2
    assert isinstance(result[1], bool)
