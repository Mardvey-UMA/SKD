package com.contentagg.aggregator.processor.content

import com.contentagg.aggregator.db.service.content.ContentService
import com.contentagg.aggregator.db.service.content.JsonConversionService
import com.contentagg.aggregator.db.service.content.RelatedContentService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils

@DisplayName("QueryContentProcessor — S3 URL absolutization")
class QueryContentProcessorTest {

    private val contentService: ContentService = mockk(relaxed = true)
    private val jsonConversionService: JsonConversionService = mockk(relaxed = true)
    private val relatedContentService: RelatedContentService = mockk(relaxed = true)

    private lateinit var processor: QueryContentProcessor

    @BeforeEach
    fun setUp() {
        processor = QueryContentProcessor(contentService, jsonConversionService, relatedContentService)
        ReflectionTestUtils.setField(processor, "s3PublicUrl", "http://localhost:9002")
    }

    @Nested
    @DisplayName("absolutizeHtml")
    inner class AbsolutizeHtmlTests {

        @Test
        @DisplayName("replaces relative src double-quote with absolute URL")
        fun relativeDoubleQuote_replaced() {
            val input = """<img src="/content-media/habr/2026/img.jpg" alt="test">"""
            val result = callAbsolutizeHtml(input)
            assertEquals(
                """<img src="http://localhost:9002/content-media/habr/2026/img.jpg" alt="test">""",
                result
            )
        }

        @Test
        @DisplayName("replaces relative src single-quote with absolute URL")
        fun relativeSingleQuote_replaced() {
            val input = """<img src='/content-media/telegram/2026/img.jpg'>"""
            val result = callAbsolutizeHtml(input)
            assertEquals(
                """<img src='http://localhost:9002/content-media/telegram/2026/img.jpg'>""",
                result
            )
        }

        @Test
        @DisplayName("does NOT touch already-absolute URLs")
        fun absoluteUrl_untouched() {
            val input = """<img src="http://other-server.com/content-media/img.jpg">"""
            val result = callAbsolutizeHtml(input)
            assertEquals(input, result)
        }

        @Test
        @DisplayName("does NOT touch non-media src attributes")
        fun nonMediaSrc_untouched() {
            val input = """<img src="/static/logo.png">"""
            val result = callAbsolutizeHtml(input)
            assertEquals(input, result)
        }

        @Test
        @DisplayName("handles multiple images in one HTML string")
        fun multipleImages_allReplaced() {
            val input = """<p><img src="/content-media/img1.jpg"> and <img src='/content-media/img2.jpg'></p>"""
            val result = callAbsolutizeHtml(input)
            assertEquals(
                """<p><img src="http://localhost:9002/content-media/img1.jpg"> and <img src='http://localhost:9002/content-media/img2.jpg'></p>""",
                result
            )
        }

        @Test
        @DisplayName("returns null for null input")
        fun nullInput_returnsNull() {
            val result = callAbsolutizeHtml(null)
            assertNull(result)
        }

        @Test
        @DisplayName("returns empty string for empty input")
        fun emptyInput_returnsEmpty() {
            val result = callAbsolutizeHtml("")
            assertEquals("", result)
        }

        @Test
        @DisplayName("html without any img tags is returned unchanged")
        fun noImgTags_unchanged() {
            val input = "<p>Some text content without images</p>"
            val result = callAbsolutizeHtml(input)
            assertEquals(input, result)
        }
    }

    @Nested
    @DisplayName("absolutizeMedia")
    inner class AbsolutizeMediaTests {

        @Test
        @DisplayName("adds url field built from s3PublicUrl and key")
        fun addsAbsoluteUrl() {
            val media = listOf(
                mapOf("key" to "habr/2026/img.jpg", "type" to "image/jpeg")
            )
            val result = callAbsolutizeMedia(media)!!
            assertEquals(1, result.size)
            assertEquals("http://localhost:9002/habr/2026/img.jpg", result[0]["url"])
            assertEquals("habr/2026/img.jpg", result[0]["key"])
            assertEquals("image/jpeg", result[0]["type"])
        }

        @Test
        @DisplayName("items without key field are passed through unchanged")
        fun noKey_passedThrough() {
            val media = listOf<Map<String, Any>>(
                mapOf("url" to "https://example.com/img.jpg", "type" to "image/jpeg")
            )
            val result = callAbsolutizeMedia(media)!!
            assertEquals("https://example.com/img.jpg", result[0]["url"])
        }

        @Test
        @DisplayName("multiple items are all processed")
        fun multipleItems_allProcessed() {
            val media = listOf(
                mapOf("key" to "habr/img1.jpg", "type" to "image/jpeg"),
                mapOf("key" to "telegram/video.mp4", "type" to "video/mp4")
            )
            val result = callAbsolutizeMedia(media)!!
            assertEquals("http://localhost:9002/habr/img1.jpg", result[0]["url"])
            assertEquals("http://localhost:9002/telegram/video.mp4", result[1]["url"])
        }

        @Test
        @DisplayName("returns null for null input")
        fun nullInput_returnsNull() {
            val result = callAbsolutizeMedia(null)
            assertNull(result)
        }

        @Test
        @DisplayName("returns empty list for empty list input")
        fun emptyList_returnsEmpty() {
            val result = callAbsolutizeMedia(emptyList())
            assertNotNull(result)
            assertEquals(0, result!!.size)
        }

        @Test
        @DisplayName("overrides existing url field if key is present")
        fun existingUrl_overriddenByKey() {
            val media = listOf(
                mapOf("key" to "habr/img.jpg", "url" to "http://old-server.com/img.jpg", "type" to "image/jpeg")
            )
            val result = callAbsolutizeMedia(media)!!
            assertEquals("http://localhost:9002/habr/img.jpg", result[0]["url"])
        }
    }

    // Access private methods via reflection for unit testing
    private fun callAbsolutizeHtml(html: String?): String? {
        val method = QueryContentProcessor::class.java.getDeclaredMethod("absolutizeHtml", String::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(processor, html) as String?
    }

    private fun callAbsolutizeMedia(media: List<Map<String, Any>>?): List<Map<String, Any>>? {
        val method = QueryContentProcessor::class.java.getDeclaredMethod("absolutizeMedia", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(processor, media) as List<Map<String, Any>>?
    }
}
