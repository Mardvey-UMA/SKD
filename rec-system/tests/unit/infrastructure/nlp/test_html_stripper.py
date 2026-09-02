"""Unit tests for HTML stripper utility -- RED phase."""
from __future__ import annotations

import pytest

from src.infrastructure.nlp.html_stripper import strip_html


class TestStripHtml:
    def test_strips_paragraph_tags(self):
        assert strip_html("<p>Hello</p>") == "Hello"

    def test_strips_multiple_paragraphs(self):
        result = strip_html("<p>Line 1</p><p>Line 2</p>")
        assert "Line 1" in result
        assert "Line 2" in result

    def test_empty_string_returns_empty(self):
        assert strip_html("") == ""

    def test_none_returns_empty(self):
        assert strip_html(None) == ""

    def test_plain_text_unchanged(self):
        assert strip_html("no tags here") == "no tags here"

    def test_strips_anchor_tag(self):
        assert strip_html("<a href='url'>link</a>") == "link"

    def test_decodes_html_entities(self):
        result = strip_html("&amp; &lt; &gt;")
        assert result == "& < >"

    def test_strips_img_tag(self):
        result = strip_html("<img src='x'/> text")
        assert result.strip() == "text"

    def test_strips_div_tags(self):
        assert strip_html("<div>content</div>") == "content"

    def test_strips_heading_tags(self):
        assert strip_html("<h1>Title</h1>") == "Title"
        assert strip_html("<h2>Sub</h2>") == "Sub"

    def test_strips_list_tags(self):
        result = strip_html("<ul><li>item1</li><li>item2</li></ul>")
        assert "item1" in result
        assert "item2" in result

    def test_strips_br_tags(self):
        result = strip_html("line1<br/>line2")
        assert "line1" in result
        assert "line2" in result

    def test_strips_code_and_pre_tags(self):
        result = strip_html("<pre><code>x = 1</code></pre>")
        assert "x = 1" in result

    def test_collapses_multiple_whitespace(self):
        result = strip_html("<p>  hello   world  </p>")
        assert result == "hello world"

    def test_decodes_nbsp(self):
        result = strip_html("hello&nbsp;world")
        assert "hello" in result
        assert "world" in result

    def test_strips_inline_style(self):
        result = strip_html('<span style="color:red">text</span>')
        assert result == "text"
