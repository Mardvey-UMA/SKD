package com.contentagg.parser.processor.habr

import com.contentagg.parser.db.service.rawcontent.RawContentService
import com.contentagg.parser.db.service.util.HabrSourceSubtype
import com.contentagg.parser.db.service.util.JsonConversionService
import com.contentagg.parser.exception.HabrParseException
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import com.contentagg.parser.integration.rest.habr.HabrApiClient
import com.contentagg.parser.integration.s3.HabrImageUploadService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Processor for common Habr parsing logic.
 * Coordinates API calls, image processing, and persistence to raw_content.
 *
 * Simplified flow (task 13):
 * - Fetch article IDs from Habr API
 * - For each article: download images to S3, build rawData JSON, save to raw_content
 * - isProcessedByDedup and isPublished default to false — downstream jobs handle pipeline stages
 *
 * Removed: deduplication check, protobuf event building, HabrEventBuilder injection.
 */
@Component
class HabrParseProcessor(
    private val habrApiClient: HabrApiClient,
    private val rawContentService: RawContentService,
    private val habrImageUploadService: HabrImageUploadService,
    private val jsonConversionService: JsonConversionService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(HabrParseProcessor::class.java)
        private const val PER_PAGE = 20
        private const val MAX_PAGES = 10
    }

    /**
     * Parse articles from Habr based on subtype.
     * Saves parsed content to raw_content with isProcessedByDedup = false, isPublished = false.
     *
     * @return count of saved records
     */
    fun parseArticles(
        sourceConfig: SourceConfigResponse,
        subtype: HabrSourceSubtype,
        alias: String,
        maxArticles: Int,
    ): Int {
        val context = HabrParseContext.from(sourceConfig, subtype, alias, maxArticles)
        var savedCount = 0

        try {
            val articleIds = fetchArticleIds(context)
            log.info("Fetched {} article IDs for alias={}", articleIds.size, context.alias)

            var processed = 0
            for (articleId in articleIds) {
                if (processed >= maxArticles) break
                try {
                    val saved = processArticle(context, articleId)
                    if (saved) {
                        savedCount++
                        processed++
                    }
                } catch (e: Exception) {
                    log.error("Error processing articleId={}: {}", articleId, e.message)
                }
            }

            log.info("Saved {} articles for sourceId={}", savedCount, context.sourceId)
            return savedCount

        } catch (e: Exception) {
            log.error("Error parsing Habr content for sourceId={}: {}", context.sourceId, e.message)
            throw HabrParseException("Failed to parse Habr content: ${e.message}", e)
        }
    }

    private fun fetchArticleIds(context: HabrParseContext): List<String> {
        val allIds = mutableListOf<String>()
        var page = 1

        while (allIds.size < context.maxArticles && page <= MAX_PAGES) {
            val response = when (context.subtype) {
                HabrSourceSubtype.USER ->
                    habrApiClient.getArticlesByUser(context.alias, page, PER_PAGE)
                HabrSourceSubtype.COMPANY ->
                    habrApiClient.getArticlesByCompany(context.alias, page, PER_PAGE)
                HabrSourceSubtype.HUB ->
                    habrApiClient.getArticlesByHub(context.alias, page, PER_PAGE)
            }

            if (response.publicationIds.isNullOrEmpty()) break
            allIds.addAll(response.publicationIds)
            page++
        }

        return allIds.take(context.maxArticles)
    }

    /**
     * Process a single article: download images to S3, build rawData JSON, save to raw_content.
     *
     * rawData JSON includes `content` (processed HTML with S3 URLs) and `contentFormat` so that
     * RawContentEventBuilder (task 15) can extract them when building ContentParsedEvent.
     * Deduplication check runs before image download to avoid wasted S3 operations.
     *
     * @return true if saved successfully, false if article not found or already processed
     */
    private fun processArticle(context: HabrParseContext, articleId: String): Boolean {
        val article = habrApiClient.getArticle(articleId) ?: run {
            log.warn("Article not found: {}", articleId)
            return false
        }

        val url = "https://habr.com/ru/articles/${article.id}/"
        val title = article.titleHtml ?: ""
        val authorId = article.author?.id ?: ""
        val externalId = article.id ?: ""
        val sourceType = "HABR_${context.subtype.name}"

        if (rawContentService.existsBySourceTypeAndExternalId(sourceType, externalId)) {
            log.debug("Skipping duplicate: sourceType={}, externalId={}", sourceType, externalId)
            return false
        }

        var processedHtml = article.textHtml ?: ""
        var downloadedMediaJson: String? = null

        if (context.parseImages && processedHtml.isNotBlank()) {
            val processed = habrImageUploadService.processImages(processedHtml, articleId)
            processedHtml = processed.htmlContent
            downloadedMediaJson = buildDownloadedMediaJson(processed.urlMapping)
        }

        val rawData = jsonConversionService.toJson(
            mapOf(
                "title" to title,
                "content" to processedHtml,
                "contentFormat" to "HTML",
                "lead" to (article.leadData?.textHtml ?: ""),
                "authorId" to authorId,
                "authorName" to (article.author?.fullname ?: ""),
                "url" to url,
                "sourceSubtype" to context.subtype.name,
                "publishedAt" to (article.timePublished?.toEpochMilli()?.toString() ?: "0"),
            )
        )

        rawContentService.saveRawContent(
            externalId = externalId,
            sourceId = context.sourceId,
            sourceType = sourceType,
            rawData = rawData ?: "{}",
            rawMedia = null,
            downloadedMedia = downloadedMediaJson,
            processingStatus = "COMPLETED",
            receivedAt = LocalDateTime.now(),
        )
        log.debug("Saved raw content for articleId={}", articleId)
        return true
    }

    private fun buildDownloadedMediaJson(urlMapping: Map<String, String>): String? {
        if (urlMapping.isEmpty()) return null
        val map = urlMapping.entries.mapIndexed { i, (originalUrl, s3Url) ->
            "media_$i" to (jsonConversionService.toJson(
                mapOf(
                    "originalUrl" to originalUrl,
                    "s3Url" to s3Url,
                )
            ) ?: "{}")
        }.toMap()
        return jsonConversionService.toJson(map)
    }
}
