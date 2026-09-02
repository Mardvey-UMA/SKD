package com.contentagg.parser.processor.vcru

import com.contentagg.parser.db.service.rawcontent.RawContentService
import com.contentagg.parser.db.service.util.JsonConversionService
import com.contentagg.parser.db.service.util.VcruSourceSubtype
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import com.contentagg.parser.integration.rest.vcru.VcruApiClient
import com.contentagg.parser.integration.rest.vcru.model.VcruArticleDto
import com.contentagg.parser.integration.s3.VcruImageUploadService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Processor for common VC.RU parsing logic.
 * Coordinates API calls, block rendering, image processing, and persistence to raw_content.
 *
 * Flow:
 * 1. Resolve subsite ID via VcruApiClient.getSubsiteInfo(alias)
 * 2. Fetch timeline with cursor-based pagination
 * 3. For each article: dedup check -> render blocks -> process images -> save raw_content
 * 4. isProcessedByDedup and isPublished default to false — downstream jobs handle pipeline stages
 */
@Component
class VcruParseProcessor(
    private val vcruApiClient: VcruApiClient,
    private val rawContentService: RawContentService,
    private val vcruBlocksRenderer: VcruBlocksRenderer,
    private val vcruImageUploadService: VcruImageUploadService,
    private val jsonConversionService: JsonConversionService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(VcruParseProcessor::class.java)
        private const val MAX_TIMELINE_REQUESTS = 10
    }

    /**
     * Parse articles from VC.RU for a specific subsite.
     * Saves parsed content to raw_content with isProcessedByDedup = false, isPublished = false.
     *
     * @return count of saved records
     */
    fun parseArticles(
        sourceConfig: SourceConfigResponse,
        subtype: VcruSourceSubtype,
        alias: String,
        maxArticles: Int,
    ): Int {
        val context = VcruParseContext.from(sourceConfig, alias, maxArticles)
        log.info("Starting VC.RU parse: alias={}, subtype={}, maxArticles={}", alias, subtype, maxArticles)

        val subsiteId = resolveSubsiteId(alias)
        if (subsiteId == null) {
            log.warn("Could not resolve subsiteId for alias={}, aborting parse", alias)
            return 0
        }

        val articles = fetchArticles(subsiteId, context.sorting, maxArticles)
        log.info("Fetched {} articles for alias={}", articles.size, alias)

        var savedCount = 0
        for (article in articles) {
            try {
                val saved = processArticle(context, article, subtype)
                if (saved) {
                    savedCount++
                }
            } catch (e: Exception) {
                log.error("Error processing vcru article id={}: {}", article.id, e.message, e)
            }
        }

        log.info("Saved {} articles for sourceId={}, alias={}", savedCount, context.sourceId, alias)
        return savedCount
    }

    /**
     * Resolve subsite alias to numeric ID.
     * Calls GET /v2.7/subsite?uri={alias}
     */
    private fun resolveSubsiteId(alias: String): Long? {
        return try {
            val subsite = vcruApiClient.getSubsiteInfo(alias)
            if (subsite?.id == null) {
                log.warn("Subsite not found or id is null for alias={}", alias)
                null
            } else {
                log.debug("Resolved subsiteId={} for alias={}", subsite.id, alias)
                subsite.id
            }
        } catch (e: Exception) {
            log.error("Failed to resolve subsiteId for alias={}: {}", alias, e.message, e)
            null
        }
    }

    /**
     * Fetch articles from timeline using cursor-based pagination.
     * Loop until maxArticles reached or hasMore=false.
     */
    private fun fetchArticles(subsiteId: Long, sorting: String, maxArticles: Int): List<VcruArticleDto> {
        val allArticles = mutableListOf<VcruArticleDto>()
        var lastId: Long? = null
        var requests = 0

        while (allArticles.size < maxArticles && requests < MAX_TIMELINE_REQUESTS) {
            val timeline = vcruApiClient.getTimeline(subsiteId, sorting, lastId)

            if (timeline == null || timeline.items.isNullOrEmpty()) {
                log.debug("Timeline returned no items: subsiteId={}, requests={}", subsiteId, requests)
                break
            }

            val entryItems = timeline.items.filter { it.type == "entry" && it.data != null }
            allArticles.addAll(entryItems.mapNotNull { it.data })
            lastId = entryItems.lastOrNull()?.data?.id

            if (timeline.hasMore != true) {
                log.debug("No more pages in timeline: subsiteId={}", subsiteId)
                break
            }

            requests++
        }

        return allArticles.take(maxArticles)
    }

    /**
     * Process a single article: dedup check, render blocks, process images, save raw_content.
     *
     * @return true if saved successfully, false if skipped (duplicate)
     */
    private fun processArticle(
        context: VcruParseContext,
        article: VcruArticleDto,
        subtype: VcruSourceSubtype,
    ): Boolean {
        val externalId = article.id?.toString() ?: run {
            log.warn("Article has null id, skipping")
            return false
        }
        val sourceType = "VCRU_${subtype.name}"

        if (rawContentService.existsBySourceTypeAndExternalId(sourceType, externalId)) {
            log.debug("Skipping duplicate: sourceType={}, externalId={}", sourceType, externalId)
            return false
        }

        val blocks = article.blocks ?: emptyList()
        var html = vcruBlocksRenderer.renderToHtml(blocks)
        var downloadedMediaJson: String? = null

        if (context.parseImages) {
            val imageUuids = vcruBlocksRenderer.extractImageUuids(blocks)
            if (imageUuids.isNotEmpty()) {
                val processed = vcruImageUploadService.processImages(html, imageUuids, externalId)
                html = processed.htmlContent
                downloadedMediaJson = buildDownloadedMediaJson(processed.urlMapping)
            }
        }

        // publishedAt: VC.RU date is Unix timestamp in seconds, multiply by 1000 to get milliseconds
        val publishedAtMillis = article.date?.times(1000)?.toString() ?: "0"

        val rawData = jsonConversionService.toJson(
            mapOf(
                "title" to (article.title ?: ""),
                "content" to html,
                "contentFormat" to "HTML",
                "lead" to (article.leadData?.text ?: ""),
                "authorId" to (article.author?.id?.toString() ?: ""),
                "authorName" to (article.author?.name ?: ""),
                "url" to (article.url ?: ""),
                "sourceSubtype" to subtype.name,
                "publishedAt" to publishedAtMillis,
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
            receivedAt = LocalDateTime.now(ZoneId.of("UTC")),
        )
        log.debug("Saved raw content for vcru articleId={}, sourceType={}", externalId, sourceType)
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
