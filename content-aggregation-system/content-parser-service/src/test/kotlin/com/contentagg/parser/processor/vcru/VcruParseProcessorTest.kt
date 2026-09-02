package com.contentagg.parser.processor.vcru

import com.contentagg.parser.db.service.rawcontent.RawContentService
import com.contentagg.parser.db.service.util.JsonConversionService
import com.contentagg.parser.db.service.util.VcruSourceSubtype
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import com.contentagg.parser.integration.rest.vcru.VcruApiClient
import com.contentagg.parser.integration.rest.vcru.model.VcruArticleDto
import com.contentagg.parser.integration.rest.vcru.model.VcruSubsiteDto
import com.contentagg.parser.integration.rest.vcru.model.VcruTimelineDto
import com.contentagg.parser.integration.rest.vcru.model.VcruTimelineItemDto
import com.contentagg.parser.integration.s3.VcruImageUploadService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class VcruParseProcessorTest {

    private lateinit var processor: VcruParseProcessor
    private val vcruApiClient: VcruApiClient = mockk()
    private val rawContentService: RawContentService = mockk(relaxed = true)
    private val vcruBlocksRenderer: VcruBlocksRenderer = mockk()
    private val vcruImageUploadService: VcruImageUploadService = mockk()
    private val jsonConversionService: JsonConversionService = mockk()

    @BeforeEach
    fun setUp() {
        processor = VcruParseProcessor(
            vcruApiClient,
            rawContentService,
            vcruBlocksRenderer,
            vcruImageUploadService,
            jsonConversionService,
        )
    }

    @Nested
    @DisplayName("parseArticles()")
    inner class ParseArticlesTests {

        @Test
        @DisplayName("returns 0 and does not save when subsite cannot be resolved")
        fun parseArticles_SubsiteNotFound_Returns0() {
            every { vcruApiClient.getSubsiteInfo("artem") } returns null

            val result = processor.parseArticles(buildSourceConfig(), VcruSourceSubtype.USER, "artem", 10)

            assertEquals(0, result)
            verify(exactly = 0) { rawContentService.saveRawContent(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("returns 0 when timeline is empty")
        fun parseArticles_EmptyTimeline_Returns0() {
            every { vcruApiClient.getSubsiteInfo("artem") } returns buildSubsiteDto(42L)
            every { vcruApiClient.getTimeline(42L, any(), any()) } returns VcruTimelineDto(items = emptyList(), hasMore = false)

            val result = processor.parseArticles(buildSourceConfig(), VcruSourceSubtype.USER, "artem", 10)

            assertEquals(0, result)
            verify(exactly = 0) { rawContentService.saveRawContent(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("skips duplicate article and does not save it")
        fun parseArticles_DuplicateArticle_Skipped() {
            val article = buildArticle(100L)
            every { vcruApiClient.getSubsiteInfo("artem") } returns buildSubsiteDto(42L)
            every { vcruApiClient.getTimeline(42L, any(), any()) } returns buildTimeline(listOf(article), hasMore = false)
            every { rawContentService.existsBySourceTypeAndExternalId("VCRU_USER", "100") } returns true
            every { vcruBlocksRenderer.renderToHtml(any()) } returns "<p>Content</p>"

            val result = processor.parseArticles(buildSourceConfig(), VcruSourceSubtype.USER, "artem", 10)

            assertEquals(0, result)
            verify(exactly = 0) { rawContentService.saveRawContent(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("saves new article and returns count 1")
        fun parseArticles_NewArticle_SavedAndReturns1() {
            val article = buildArticle(200L)
            every { vcruApiClient.getSubsiteInfo("artem") } returns buildSubsiteDto(42L)
            every { vcruApiClient.getTimeline(42L, any(), any()) } returns buildTimeline(listOf(article), hasMore = false)
            every { rawContentService.existsBySourceTypeAndExternalId("VCRU_USER", "200") } returns false
            every { vcruBlocksRenderer.renderToHtml(any()) } returns "<p>Content</p>"
            every { vcruBlocksRenderer.extractImageUuids(any()) } returns emptyList()
            every { jsonConversionService.toJson(any()) } returns "{}"

            val result = processor.parseArticles(buildSourceConfig(), VcruSourceSubtype.USER, "artem", 10)

            assertEquals(1, result)
            verify(exactly = 1) { rawContentService.saveRawContent(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("saves with sourceType VCRU_USER when subtype is USER")
        fun parseArticles_SavesWithCorrectSourceType_VcruUser() {
            val article = buildArticle(300L)
            every { vcruApiClient.getSubsiteInfo("artem") } returns buildSubsiteDto(42L)
            every { vcruApiClient.getTimeline(42L, any(), any()) } returns buildTimeline(listOf(article), hasMore = false)
            every { rawContentService.existsBySourceTypeAndExternalId("VCRU_USER", "300") } returns false
            every { vcruBlocksRenderer.renderToHtml(any()) } returns "<p>Content</p>"
            every { vcruBlocksRenderer.extractImageUuids(any()) } returns emptyList()
            every { jsonConversionService.toJson(any()) } returns "{}"

            processor.parseArticles(buildSourceConfig(), VcruSourceSubtype.USER, "artem", 10)

            verify(exactly = 1) {
                rawContentService.saveRawContent(
                    externalId = "300",
                    sourceId = any(),
                    sourceType = "VCRU_USER",
                    rawData = any(),
                    rawMedia = null,
                    downloadedMedia = null,
                    processingStatus = "COMPLETED",
                    receivedAt = any(),
                )
            }
        }

        @Test
        @DisplayName("does not call VcruImageUploadService when parseImages=false")
        fun parseArticles_ParseImagesDisabled_ImageServicesNotCalled() {
            val article = buildArticle(400L)
            val sourceConfig = buildSourceConfig(parseImages = false)
            every { vcruApiClient.getSubsiteInfo("artem") } returns buildSubsiteDto(42L)
            every { vcruApiClient.getTimeline(42L, any(), any()) } returns buildTimeline(listOf(article), hasMore = false)
            every { rawContentService.existsBySourceTypeAndExternalId("VCRU_USER", "400") } returns false
            every { vcruBlocksRenderer.renderToHtml(any()) } returns "<p>Content</p>"
            every { jsonConversionService.toJson(any()) } returns "{}"

            processor.parseArticles(sourceConfig, VcruSourceSubtype.USER, "artem", 10)

            verify(exactly = 0) { vcruImageUploadService.processImages(any(), any(), any()) }
        }

        @Test
        @DisplayName("fetches multiple pages when hasMore=true on first call")
        fun parseArticles_CursorPagination_FetchesMultiplePages() {
            val article1 = buildArticle(501L)
            val article2 = buildArticle(502L)

            every { vcruApiClient.getSubsiteInfo("artem") } returns buildSubsiteDto(42L)
            // First call: hasMore=true, returns one article
            every { vcruApiClient.getTimeline(42L, any(), null) } returns buildTimeline(listOf(article1), hasMore = true)
            // Second call with lastId: hasMore=false
            every { vcruApiClient.getTimeline(42L, any(), 501L) } returns buildTimeline(listOf(article2), hasMore = false)

            every { rawContentService.existsBySourceTypeAndExternalId("VCRU_USER", "501") } returns false
            every { rawContentService.existsBySourceTypeAndExternalId("VCRU_USER", "502") } returns false
            every { vcruBlocksRenderer.renderToHtml(any()) } returns "<p>Content</p>"
            every { vcruBlocksRenderer.extractImageUuids(any()) } returns emptyList()
            every { jsonConversionService.toJson(any()) } returns "{}"

            val result = processor.parseArticles(buildSourceConfig(), VcruSourceSubtype.USER, "artem", 10)

            assertEquals(2, result)
            verify(exactly = 1) { vcruApiClient.getTimeline(42L, any(), null) }
            verify(exactly = 1) { vcruApiClient.getTimeline(42L, any(), 501L) }
        }
    }

    // -------------------------------------------------------------------------
    // Builder helpers
    // -------------------------------------------------------------------------

    private fun buildSourceConfig(parseImages: Boolean = false): SourceConfigResponse = SourceConfigResponse(
        id = "550e8400-e29b-41d4-a716-446655440000",
        sourceType = null,
        name = "vcru-test-source",
        url = "https://vc.ru/artem",
        updateFrequencyMinutes = 5,
        isActive = true,
        parameters = mapOf(
            "vcruAlias" to "artem",
            "parseImages" to parseImages.toString(),
            "maxArticles" to "10",
            "sorting" to "new",
        ),
        createdAt = null,
        updatedAt = null,
    )

    private fun buildSubsiteDto(id: Long): VcruSubsiteDto = VcruSubsiteDto(
        id = id,
        uri = "artem",
        url = "https://vc.ru/artem",
        type = 1,
        subtype = null,
        name = "Artem",
        nickname = "artem",
        description = null,
        counters = null,
    )

    private fun buildArticle(id: Long): VcruArticleDto = VcruArticleDto(
        id = id,
        title = "Test Article $id",
        date = 1711708800L,
        dateModified = null,
        blocks = emptyList(),
        url = "https://vc.ru/artem/$id",
        counters = null,
        author = null,
        subsite = null,
        isPublished = true,
        leadData = null,
    )

    private fun buildTimeline(articles: List<VcruArticleDto>, hasMore: Boolean): VcruTimelineDto {
        val items = articles.map { article ->
            VcruTimelineItemDto(type = "entry", data = article)
        }
        return VcruTimelineDto(items = items, hasMore = hasMore)
    }
}
