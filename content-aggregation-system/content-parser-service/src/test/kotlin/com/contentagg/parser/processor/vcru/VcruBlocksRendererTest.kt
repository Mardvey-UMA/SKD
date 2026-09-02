package com.contentagg.parser.processor.vcru

import com.contentagg.parser.integration.rest.vcru.model.VcruBlockDataDto
import com.contentagg.parser.integration.rest.vcru.model.VcruBlockDto
import com.contentagg.parser.integration.rest.vcru.model.VcruBlockImageDataDto
import com.contentagg.parser.integration.rest.vcru.model.VcruBlockImageDto
import com.contentagg.parser.integration.rest.vcru.model.VcruBlockMediaItemDto
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class VcruBlocksRendererTest {

    private lateinit var renderer: VcruBlocksRenderer

    private val testCdnUrl = "https://leonardo.osnova.io"
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        renderer = VcruBlocksRenderer(testCdnUrl)
    }

    private fun toJsonNode(items: List<VcruBlockMediaItemDto>): JsonNode =
        objectMapper.valueToTree(items)

    // -------------------------------------------------------------------------
    // renderToHtml() tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("renderToHtml()")
    inner class RenderToHtmlTests {

        @Test
        @DisplayName("returns empty string for empty blocks list")
        fun renderToHtml_EmptyBlocks_ReturnsEmptyString() {
            val result = renderer.renderToHtml(emptyList())
            assertEquals("", result)
        }

        @Test
        @DisplayName("text block renders as <p> tag")
        fun renderToHtml_TextBlock_ReturnsPTag() {
            val block = buildTextBlock("Test text")
            val result = renderer.renderToHtml(listOf(block))
            assertEquals("<p>Test text</p>", result)
        }

        @Test
        @DisplayName("header block with level=2 renders as <h2> tag")
        fun renderToHtml_HeaderBlock_ReturnsHTag() {
            val block = buildHeaderBlock("Title", level = 2)
            val result = renderer.renderToHtml(listOf(block))
            assertEquals("<h2>Title</h2>", result)
        }

        @Test
        @DisplayName("header block with null level defaults to <h2>")
        fun renderToHtml_HeaderBlock_DefaultLevelWhenNull_ReturnsH2() {
            val block = buildHeaderBlock("Title", level = null)
            val result = renderer.renderToHtml(listOf(block))
            assertEquals("<h2>Title</h2>", result)
        }

        @Test
        @DisplayName("media block with 2 items renders 2 <img> tags with CDN URLs")
        fun renderToHtml_MediaBlock_ReturnsImgTags() {
            val uuid1 = "uuid-001"
            val uuid2 = "uuid-002"
            val items = listOf(
                buildMediaItem(uuid1, "Image 1"),
                buildMediaItem(uuid2, "Image 2"),
            )
            val block = buildMediaBlock(items)
            val result = renderer.renderToHtml(listOf(block))

            assertTrue(result.contains("<img src=\"$testCdnUrl/$uuid1/\" alt=\"Image 1\" />"))
            assertTrue(result.contains("<img src=\"$testCdnUrl/$uuid2/\" alt=\"Image 2\" />"))
        }

        @Test
        @DisplayName("quote block renders as <blockquote>")
        fun renderToHtml_QuoteBlock_ReturnsBlockquote() {
            val block = buildQuoteBlock("Quote text")
            val result = renderer.renderToHtml(listOf(block))
            assertEquals("<blockquote>Quote text</blockquote>", result)
        }

        @Test
        @DisplayName("list block with 3 items renders as <ul> with 3 <li> elements")
        fun renderToHtml_ListBlock_ReturnsUlWithLiItems() {
            val items = listOf(
                buildListItem("Item 1"),
                buildListItem("Item 2"),
                buildListItem("Item 3"),
            )
            val block = buildListBlock(items)
            val result = renderer.renderToHtml(listOf(block))

            assertTrue(result.contains("<ul>"))
            assertTrue(result.contains("</ul>"))
            assertTrue(result.contains("<li>Item 1</li>"))
            assertTrue(result.contains("<li>Item 2</li>"))
            assertTrue(result.contains("<li>Item 3</li>"))
        }

        @Test
        @DisplayName("code block renders as <pre><code>")
        fun renderToHtml_CodeBlock_ReturnsPreCode() {
            val block = buildCodeBlock("val x = 1")
            val result = renderer.renderToHtml(listOf(block))
            assertEquals("<pre><code>val x = 1</code></pre>", result)
        }

        @Test
        @DisplayName("embed block renders as HTML comment with embed type")
        fun renderToHtml_EmbedBlock_ReturnsHtmlComment() {
            val block = buildEmbedBlock("youtube")
            val result = renderer.renderToHtml(listOf(block))
            assertEquals("<!-- embed: youtube -->", result)
        }

        @Test
        @DisplayName("unknown block type is skipped silently without output")
        fun renderToHtml_UnknownBlockType_SkippedSilently() {
            val block = VcruBlockDto(
                type = "unknown_custom_type",
                data = VcruBlockDataDto(text = "some content", level = null, items = null, type = null),
                cover = null
            )
            val result = renderer.renderToHtml(listOf(block))
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("cover block (cover=true) is skipped and produces no output")
        fun renderToHtml_CoverBlock_Skipped() {
            val block = VcruBlockDto(
                type = "media",
                data = VcruBlockDataDto(
                    text = null,
                    level = null,
                    items = toJsonNode(listOf(buildMediaItem("cover-uuid", "Cover"))),
                    type = null
                ),
                cover = true
            )
            val result = renderer.renderToHtml(listOf(block))
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("block with null data is skipped without throwing NPE")
        fun renderToHtml_BlockWithNullData_Skipped() {
            val block = VcruBlockDto(type = "text", data = null, cover = null)
            val result = renderer.renderToHtml(listOf(block))
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("multiple blocks are joined with newlines between them")
        fun renderToHtml_MultipleBlocks_JoinedWithNewlines() {
            val textBlock = buildTextBlock("Hello")
            val quoteBlock = buildQuoteBlock("World")
            val result = renderer.renderToHtml(listOf(textBlock, quoteBlock))

            assertTrue(result.contains("\n"))
            assertEquals("<p>Hello</p>\n<blockquote>World</blockquote>", result)
        }
    }

    // -------------------------------------------------------------------------
    // extractImageUuids() tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("extractImageUuids()")
    inner class ExtractImageUuidsTests {

        @Test
        @DisplayName("returns UUIDs from media blocks")
        fun extractImageUuids_MediaBlocks_ReturnsUuids() {
            val uuid1 = "test-uuid-aaa"
            val uuid2 = "test-uuid-bbb"
            val block1 = buildMediaBlock(listOf(buildMediaItem(uuid1, "Img 1")))
            val block2 = buildMediaBlock(listOf(buildMediaItem(uuid2, "Img 2")))

            val result = renderer.extractImageUuids(listOf(block1, block2))

            assertTrue(result.contains(uuid1))
            assertTrue(result.contains(uuid2))
            assertEquals(2, result.size)
        }

        @Test
        @DisplayName("cover=true image blocks are excluded from UUID extraction")
        fun extractImageUuids_CoverBlockExcluded_NotInResult() {
            val coverUuid = "cover-image-uuid"
            val regularUuid = "regular-image-uuid"

            val coverBlock = VcruBlockDto(
                type = "media",
                data = VcruBlockDataDto(
                    text = null,
                    level = null,
                    items = toJsonNode(listOf(buildMediaItem(coverUuid, "Cover"))),
                    type = null
                ),
                cover = true
            )
            val regularBlock = buildMediaBlock(listOf(buildMediaItem(regularUuid, "Regular")))

            val result = renderer.extractImageUuids(listOf(coverBlock, regularBlock))

            assertFalse(result.contains(coverUuid))
            assertTrue(result.contains(regularUuid))
        }

        @Test
        @DisplayName("non-media blocks (text, header, quote) produce empty UUID list")
        fun extractImageUuids_NonMediaBlocks_ReturnsEmpty() {
            val blocks = listOf(
                buildTextBlock("Hello"),
                buildHeaderBlock("Title", 1),
                buildQuoteBlock("Quote"),
            )
            val result = renderer.extractImageUuids(blocks)
            assertTrue(result.isEmpty())
        }
    }

    // -------------------------------------------------------------------------
    // buildCdnUrl() tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("buildCdnUrl()")
    inner class BuildCdnUrlTests {

        @Test
        @DisplayName("returns CDN URL with trailing slash in correct format")
        fun buildCdnUrl_ReturnsCorrectUrl() {
            val uuid = "test-uuid-123"
            val result = renderer.buildCdnUrl(uuid)
            assertEquals("https://leonardo.osnova.io/$uuid/", result)
        }
    }

    // -------------------------------------------------------------------------
    // Builder helpers
    // -------------------------------------------------------------------------

    private fun buildTextBlock(text: String): VcruBlockDto = VcruBlockDto(
        type = "text",
        data = VcruBlockDataDto(text = text, level = null, items = null, type = null),
        cover = null
    )

    private fun buildHeaderBlock(text: String, level: Int?): VcruBlockDto = VcruBlockDto(
        type = "header",
        data = VcruBlockDataDto(text = text, level = level, items = null, type = null),
        cover = null
    )

    private fun buildQuoteBlock(text: String): VcruBlockDto = VcruBlockDto(
        type = "quote",
        data = VcruBlockDataDto(text = text, level = null, items = null, type = null),
        cover = null
    )

    private fun buildCodeBlock(text: String): VcruBlockDto = VcruBlockDto(
        type = "code",
        data = VcruBlockDataDto(text = text, level = null, items = null, type = null),
        cover = null
    )

    private fun buildEmbedBlock(embedType: String): VcruBlockDto = VcruBlockDto(
        type = "embed",
        data = VcruBlockDataDto(text = null, level = null, items = null, type = embedType),
        cover = null
    )

    private fun buildMediaBlock(items: List<VcruBlockMediaItemDto>): VcruBlockDto = VcruBlockDto(
        type = "media",
        data = VcruBlockDataDto(text = null, level = null, items = toJsonNode(items), type = null),
        cover = null
    )

    private fun buildListBlock(items: List<VcruBlockMediaItemDto>): VcruBlockDto = VcruBlockDto(
        type = "list",
        data = VcruBlockDataDto(text = null, level = null, items = toJsonNode(items), type = null),
        cover = null
    )

    private fun buildMediaItem(uuid: String, title: String): VcruBlockMediaItemDto = VcruBlockMediaItemDto(
        title = title,
        image = VcruBlockImageDto(
            type = "image",
            data = VcruBlockImageDataDto(
                uuid = uuid,
                width = 800,
                height = 600,
                size = 102400L,
                type = "image/jpeg"
            )
        )
    )

    private fun buildListItem(text: String): VcruBlockMediaItemDto = VcruBlockMediaItemDto(
        title = text,
        image = null
    )
}
