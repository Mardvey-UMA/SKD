package com.contentagg.parser.sanitization

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HtmlSanitizerTest {

    private lateinit var sanitizer: HtmlSanitizer

    @BeforeEach
    fun setUp() {
        sanitizer = HtmlSanitizer()
    }

    @Nested
    @DisplayName("sanitize() — dangerous tags removal")
    inner class DangerousTagsTests {

        @Test
        @DisplayName("removes script tags")
        fun removesScriptTag() {
            val html = "<p>Hello</p><script>alert('xss')</script>"
            val result = sanitizer.sanitize(html, "HABR")
            assertFalse(result.contains("<script"), "script tag must be removed")
            assertFalse(result.contains("alert('xss')"), "script content must be removed")
        }

        @Test
        @DisplayName("removes style tags")
        fun removesStyleTag() {
            val html = "<p>Content</p><style>.x{color:red}</style>"
            val result = sanitizer.sanitize(html, "HABR")
            assertFalse(result.contains("<style"), "style tag must be removed")
            assertFalse(result.contains(".x{color:red}"), "style content must be removed")
        }

        @Test
        @DisplayName("removes iframe tags")
        fun removesIframeTag() {
            val html = "<p>Video</p><iframe src='https://evil.com/tracker'></iframe>"
            val result = sanitizer.sanitize(html, "HABR")
            assertFalse(result.contains("<iframe"), "iframe tag must be removed")
            assertFalse(result.contains("evil.com"), "iframe src must be removed")
        }

        @Test
        @DisplayName("removes onclick and javascript: href attributes")
        fun removesEventHandlersAndJsHrefs() {
            val html = "<a href='javascript:void(0)' onclick='steal()'>Click</a>"
            val result = sanitizer.sanitize(html, "HABR")
            assertFalse(result.contains("onclick"), "onclick attribute must be removed")
            assertFalse(result.contains("javascript:"), "javascript: href must be removed")
        }
    }

    @Nested
    @DisplayName("sanitize() — allowed tags preservation")
    inner class AllowedTagsTests {

        @Test
        @DisplayName("keeps p tags")
        fun keepsParagraphTags() {
            val html = "<p>Hello world</p>"
            val result = sanitizer.sanitize(html, "HABR")
            assertTrue(result.contains("<p>"), "p tag must be preserved")
            assertTrue(result.contains("Hello world"), "text content must be preserved")
        }

        @Test
        @DisplayName("keeps a href tags with nofollow rel")
        fun keepsAnchorTagsWithHref() {
            val html = "<a href='https://example.com'>Link</a>"
            val result = sanitizer.sanitize(html, "HABR")
            assertTrue(result.contains("<a "), "a tag must be preserved")
            assertTrue(result.contains("href=\"https://example.com\""), "href must be preserved")
            assertTrue(result.contains("nofollow"), "nofollow rel must be added")
        }

        @Test
        @DisplayName("keeps img src tags")
        fun keepsImgTagsWithSrc() {
            val html = "<img src='/content-media/habr/2026/img.jpg' alt='image'>"
            val result = sanitizer.sanitize(html, "HABR")
            assertTrue(result.contains("<img"), "img tag must be preserved")
            assertTrue(result.contains("src="), "img src must be preserved")
        }

        @Test
        @DisplayName("keeps h2, h3, h4 heading tags")
        fun keepsHeadingTags() {
            val html = "<h2>Title</h2><h3>Subtitle</h3><h4>Section</h4>"
            val result = sanitizer.sanitize(html, "HABR")
            assertTrue(result.contains("<h2>"), "h2 must be preserved")
            assertTrue(result.contains("<h3>"), "h3 must be preserved")
            assertTrue(result.contains("<h4>"), "h4 must be preserved")
        }

        @Test
        @DisplayName("keeps code and pre tags")
        fun keepsCodeAndPreTags() {
            val html = "<pre><code>val x = 1</code></pre>"
            val result = sanitizer.sanitize(html, "HABR")
            assertTrue(result.contains("<pre>"), "pre must be preserved")
            assertTrue(result.contains("<code>"), "code must be preserved")
        }

        @Test
        @DisplayName("keeps blockquote tags")
        fun keepsBlockquoteTag() {
            val html = "<blockquote><p>Famous quote</p></blockquote>"
            val result = sanitizer.sanitize(html, "HABR")
            assertTrue(result.contains("<blockquote>"), "blockquote must be preserved")
        }

        @Test
        @DisplayName("keeps table structure")
        fun keepsTableStructure() {
            val html = "<table><tr><th>Header</th></tr><tr><td>Cell</td></tr></table>"
            val result = sanitizer.sanitize(html, "HABR")
            assertTrue(result.contains("<table>"), "table must be preserved")
            assertTrue(result.contains("<tr>"), "tr must be preserved")
            assertTrue(result.contains("<td>"), "td must be preserved")
            assertTrue(result.contains("<th>"), "th must be preserved")
        }

        @Test
        @DisplayName("keeps strong, em, b, i formatting tags")
        fun keepsFormattingTags() {
            val html = "<p><strong>Bold</strong> <em>Italic</em> <b>b</b> <i>i</i></p>"
            val result = sanitizer.sanitize(html, "HABR")
            assertTrue(result.contains("<strong>"), "strong must be preserved")
            assertTrue(result.contains("<em>"), "em must be preserved")
        }
    }

    @Nested
    @DisplayName("sanitize() — TELEGRAM source: strips img and video")
    inner class TelegramSpecificTests {

        @Test
        @DisplayName("TELEGRAM source — removes all img tags")
        fun telegramStripsAllImgTags() {
            val html = "<p>Caption</p><img src='/content-media/telegram/photo.jpg' alt='photo'>"
            val result = sanitizer.sanitize(html, "TELEGRAM")
            assertFalse(result.contains("<img"), "img tags must be stripped for TELEGRAM source")
            assertTrue(result.contains("<p>"), "p tag must remain for TELEGRAM source")
        }

        @Test
        @DisplayName("TELEGRAM source — removes video tags")
        fun telegramStripsVideoTags() {
            val html = "<p>Text</p><video src='/content-media/telegram/video.mp4'></video>"
            val result = sanitizer.sanitize(html, "TELEGRAM")
            assertFalse(result.contains("<video"), "video tags must be stripped for TELEGRAM source")
        }

        @Test
        @DisplayName("HABR source — preserves img tags")
        fun habrKeepsImgTags() {
            val html = "<img src='/content-media/habr/2026/test.jpg' alt='test'>"
            val result = sanitizer.sanitize(html, "HABR")
            assertTrue(result.contains("<img"), "img tags must be preserved for HABR source")
        }

        @Test
        @DisplayName("VCRU source — preserves img tags")
        fun vcruKeepsImgTags() {
            val html = "<img src='/content-media/vcru/2026/test.webp' alt='test'>"
            val result = sanitizer.sanitize(html, "VCRU")
            assertTrue(result.contains("<img"), "img tags must be preserved for VCRU source")
        }

        @Test
        @DisplayName("TELEGRAM source is case-insensitive")
        fun telegramIsCaseInsensitive() {
            val html = "<p>Text</p><img src='/content-media/telegram/photo.jpg'>"
            val resultLower = sanitizer.sanitize(html, "telegram")
            val resultUpper = sanitizer.sanitize(html, "TELEGRAM")
            assertFalse(resultLower.contains("<img"), "case-insensitive match: telegram (lower) must strip img")
            assertFalse(resultUpper.contains("<img"), "case-insensitive match: TELEGRAM (upper) must strip img")
        }
    }

    @Nested
    @DisplayName("sanitize() — idempotency")
    inner class IdempotencyTests {

        @Test
        @DisplayName("sanitizing the same HTML twice produces the same result")
        fun sanitizeIsIdempotent() {
            val html = "<p>Hello <strong>world</strong></p><img src='/content-media/img.jpg' alt='x'><script>bad()</script>"
            val firstPass = sanitizer.sanitize(html, "HABR")
            val secondPass = sanitizer.sanitize(firstPass, "HABR")
            assertEquals(firstPass, secondPass, "sanitize must be idempotent")
        }

        @Test
        @DisplayName("sanitizing TELEGRAM twice still strips img")
        fun sanitizeTelegramIsIdempotent() {
            val html = "<p>Caption</p><img src='/photo.jpg'>"
            val firstPass = sanitizer.sanitize(html, "TELEGRAM")
            val secondPass = sanitizer.sanitize(firstPass, "TELEGRAM")
            assertEquals(firstPass, secondPass, "sanitize TELEGRAM must be idempotent")
            assertFalse(secondPass.contains("<img"), "img must still be gone after second pass")
        }
    }

    @Nested
    @DisplayName("sanitize() — null and empty input")
    inner class NullAndEmptyTests {

        @Test
        @DisplayName("null input returns empty string")
        fun nullInputReturnsEmpty() {
            val result = sanitizer.sanitize(null, "HABR")
            assertEquals("", result)
        }

        @Test
        @DisplayName("blank input returns empty string")
        fun blankInputReturnsEmpty() {
            val result = sanitizer.sanitize("   ", "HABR")
            assertEquals("", result)
        }

        @Test
        @DisplayName("empty string returns empty string")
        fun emptyInputReturnsEmpty() {
            val result = sanitizer.sanitize("", "HABR")
            assertEquals("", result)
        }
    }

    @Nested
    @DisplayName("toPlainText()")
    inner class ToPlainTextTests {

        @Test
        @DisplayName("extracts plain text from HTML")
        fun extractsPlainText() {
            val html = "<p>Hello <strong>world</strong></p>"
            val result = sanitizer.toPlainText(html)
            assertTrue(result.contains("Hello"), "should contain Hello")
            assertTrue(result.contains("world"), "should contain world")
            assertFalse(result.contains("<"), "should not contain HTML tags")
        }

        @Test
        @DisplayName("blank HTML returns empty string")
        fun blankHtmlReturnsEmpty() {
            assertEquals("", sanitizer.toPlainText(""))
            assertEquals("", sanitizer.toPlainText("   "))
        }

        @Test
        @DisplayName("collapses multiple spaces")
        fun collapsesSpaces() {
            val html = "<p>Hello   world</p>"
            val result = sanitizer.toPlainText(html)
            assertFalse(result.contains("  "), "should not contain consecutive spaces")
        }
    }

    @Nested
    @DisplayName("buildPreview()")
    inner class BuildPreviewTests {

        @Test
        @DisplayName("short text — returns as-is")
        fun shortTextReturnedAsIs() {
            val text = "Short preview."
            val result = sanitizer.buildPreview(text, 300)
            assertEquals(text, result)
        }

        @Test
        @DisplayName("long text — truncated at sentence boundary found past midpoint")
        fun longTextTruncatedAtSentenceBoundary() {
            // Sentence boundary at position 200 (past midpoint of 150), followed by long text.
            // buildPreview should cut at the sentence boundary, result <= maxChars.
            val sentencePart = "x".repeat(199) + ". "
            val text = sentencePart + "y".repeat(500)
            val result = sanitizer.buildPreview(text, 300)
            // The '.' is at index 199, which is > 300/2 = 150, so sentence boundary is used
            assertTrue(result.endsWith("."), "preview should end at sentence boundary")
            assertTrue(result.length <= 300, "preview truncated at boundary should not exceed maxChars")
        }

        @Test
        @DisplayName("long text without sentence boundary — truncated with ellipsis")
        fun longTextWithoutSentenceBoundaryHasEllipsis() {
            val text = "x".repeat(500)
            val result = sanitizer.buildPreview(text, 300)
            assertTrue(result.endsWith("..."), "should end with ellipsis when no sentence boundary found")
            assertTrue(result.length <= 303, "ellipsis preview should be close to maxChars")
        }

        @Test
        @DisplayName("text exactly at maxChars — returned as-is")
        fun textAtExactLimitReturnedAsIs() {
            val text = "x".repeat(300)
            val result = sanitizer.buildPreview(text, 300)
            assertEquals(text, result)
        }
    }
}
