package com.contentagg.parser.processor.habr

import com.contentagg.parser.db.service.rawcontent.RawContentService
import com.contentagg.parser.db.service.util.HabrSourceSubtype
import com.contentagg.parser.db.service.util.JsonConversionService
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import com.contentagg.parser.integration.rest.habr.HabrApiClient
import com.contentagg.parser.integration.rest.habr.model.HabrArticleDto
import com.contentagg.parser.integration.rest.habr.model.HabrArticleListDto
import com.contentagg.parser.integration.s3.HabrImageUploadService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HabrParseProcessorTest {

    private lateinit var processor: HabrParseProcessor
    private val habrApiClient: HabrApiClient = mockk()
    private val rawContentService: RawContentService = mockk(relaxed = true)
    private val habrImageUploadService: HabrImageUploadService = mockk()
    private val jsonConversionService: JsonConversionService = mockk()

    @BeforeEach
    fun setUp() {
        processor = HabrParseProcessor(
            habrApiClient,
            rawContentService,
            habrImageUploadService,
            jsonConversionService,
        )
    }

    @Nested
    @DisplayName("parseArticles()")
    inner class ParseArticlesTests {

        @Test
        @DisplayName("returns 0 when API returns empty article list")
        fun empty_articles() {
            val sourceConfig = buildSourceConfig()
            every { habrApiClient.getArticlesByHub(any(), any(), any()) } returns
                HabrArticleListDto(pagesCount = 0, publicationIds = emptyList(), publicationRefs = emptyMap())

            val result = processor.parseArticles(sourceConfig, HabrSourceSubtype.HUB, "kotlin", 10)

            assertEquals(0, result)
            verify(exactly = 0) { rawContentService.saveRawContent(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("skips duplicate articles via existsBySourceTypeAndExternalId")
        fun skips_duplicates() {
            val sourceConfig = buildSourceConfig()
            val articleId = "123"
            val article = buildArticle(articleId)

            every { habrApiClient.getArticlesByHub(any(), any(), any()) } returns
                HabrArticleListDto(pagesCount = 1, publicationIds = listOf(articleId), publicationRefs = emptyMap())
            every { habrApiClient.getArticle(articleId) } returns article
            every { rawContentService.existsBySourceTypeAndExternalId("HABR_HUB", articleId) } returns true

            val result = processor.parseArticles(sourceConfig, HabrSourceSubtype.HUB, "kotlin", 10)

            assertEquals(0, result)
            verify(exactly = 0) { rawContentService.saveRawContent(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("saves article when not a duplicate")
        fun saves_new_article() {
            val sourceConfig = buildSourceConfig()
            val articleId = "456"
            val article = buildArticle(articleId)

            every { habrApiClient.getArticlesByHub("kotlin", 1, any()) } returns
                HabrArticleListDto(pagesCount = 1, publicationIds = listOf(articleId), publicationRefs = emptyMap())
            // Return empty list on second page to stop pagination
            every { habrApiClient.getArticlesByHub("kotlin", 2, any()) } returns
                HabrArticleListDto(pagesCount = 1, publicationIds = emptyList(), publicationRefs = emptyMap())
            every { habrApiClient.getArticle(articleId) } returns article
            every { rawContentService.existsBySourceTypeAndExternalId("HABR_HUB", articleId) } returns false
            every { jsonConversionService.toJson(any()) } returns "{}"

            val result = processor.parseArticles(sourceConfig, HabrSourceSubtype.HUB, "kotlin", 10)

            assertEquals(1, result)
            verify(exactly = 1) { rawContentService.saveRawContent(any(), any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    private fun buildSourceConfig(): SourceConfigResponse = SourceConfigResponse(
        id = "550e8400-e29b-41d4-a716-446655440000",
        sourceType = null,
        name = "test-source",
        url = "https://habr.com",
        updateFrequencyMinutes = 5,
        isActive = true,
        parameters = mapOf("parseImages" to "false"),
        createdAt = null,
        updatedAt = null,
    )

    private fun buildArticle(id: String): HabrArticleDto = HabrArticleDto(
        id = id,
        timePublished = null,
        isCorporative = false,
        lang = "ru",
        titleHtml = "Test Article",
        leadData = null,
        postType = "post",
        author = null,
        statistics = null,
        hubs = emptyList(),
        flows = emptyList(),
        textHtml = "<p>Content</p>",
        tags = emptyList(),
        metadata = null,
        readingTime = 3,
        complexity = null,
        format = null,
        status = null,
    )
}
