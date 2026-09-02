package com.contentagg.aggregator.processor.content

import com.contentagg.aggregator.api.model.content.ContentBatchItem
import com.contentagg.aggregator.api.model.content.ContentBatchRequest
import com.contentagg.aggregator.api.model.content.ContentBatchResponse
import com.contentagg.aggregator.api.model.content.ContentResponse
import com.contentagg.aggregator.api.model.content.ContentSearchRequest
import com.contentagg.aggregator.api.model.content.PageResponse
import com.contentagg.aggregator.db.repository.aggregatedcontent.model.SourceType
import com.contentagg.aggregator.db.repository.publishedcontent.model.PublishedContent
import com.contentagg.aggregator.db.service.content.ContentService
import com.contentagg.aggregator.db.service.content.JsonConversionService
import com.contentagg.aggregator.db.service.content.RelatedContentService
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class QueryContentProcessor(
    private val contentService: ContentService,
    private val jsonConversionService: JsonConversionService,
    private val relatedContentService: RelatedContentService
) {

    @Value("\${s3.public-url}")
    private lateinit var s3PublicUrl: String

    private fun absolutizeHtml(html: String?): String? {
        if (html.isNullOrEmpty()) return html
        return html.replace("src=\"/content-media/", "src=\"$s3PublicUrl/content-media/")
                  .replace("src='/content-media/", "src='$s3PublicUrl/content-media/")
    }

    private fun absolutizeMedia(media: List<Map<String, Any>>?): List<Map<String, Any>>? {
        if (media.isNullOrEmpty()) return media
        return media.map { item ->
            val key = item["key"] as? String
            if (key != null) {
                val mutable = item.toMutableMap()
                mutable["url"] = "$s3PublicUrl/$key"
                mutable
            } else {
                item
            }
        }
    }

    fun searchContent(request: ContentSearchRequest): PageResponse<ContentResponse> {
        val page = contentService.searchContent(
            sourceType = request.sourceType,
            sourceId = request.sourceId,
            search = request.search,
            fromDate = request.fromDate,
            toDate = request.toDate,
            page = request.page,
            size = request.size,
            sortBy = request.sortBy,
            sortDirection = request.sortDirection
        )
        return toPageResponse(page)
    }

    fun getContentById(id: UUID): ContentResponse {
        val content = contentService.getContentById(id)
        return toResponse(content)
    }

    fun getLatestContent(page: Int, size: Int): PageResponse<ContentResponse> {
        val result = contentService.getLatestContent(page, size)
        return toPageResponse(result)
    }

    fun getContentBySourceType(sourceType: SourceType, page: Int, size: Int): PageResponse<ContentResponse> {
        val result = contentService.getContentBySourceType(sourceType, page, size)
        return toPageResponse(result)
    }

    fun getContentByIds(ids: List<UUID>): List<ContentResponse> {
        return contentService.findByIds(ids).map { toResponse(it) }
    }

    fun getContentBatch(request: ContentBatchRequest): ContentBatchResponse {
        val dedupedIds = request.ids.distinct()
        val found = if (dedupedIds.isEmpty()) emptyList() else contentService.findByIds(dedupedIds)
        val foundIds = found.map { it.id!! }.toSet()
        val notFound = dedupedIds.filter { it !in foundIds }.map { it.toString() }

        val relatedByArticleId: Map<Long, List<UUID>> = if (request.includeRelated) {
            val articleIds = found.mapNotNull { it.dedupArticleId }
            if (articleIds.isNotEmpty()) {
                relatedContentService.findRelatedIds(articleIds, request.relatedLimit)
            } else {
                emptyMap()
            }
        } else {
            emptyMap()
        }

        val items = found.associate { content ->
            val relatedIds: List<String>? = if (request.includeRelated) {
                val articleId = content.dedupArticleId
                if (articleId != null) {
                    relatedByArticleId[articleId]
                        ?.filter { it != content.id }
                        ?.map { it.toString() }
                        ?: emptyList()
                } else {
                    emptyList()
                }
            } else {
                null
            }
            content.id!!.toString() to toBatchItem(content, relatedIds)
        }

        return ContentBatchResponse(items = items, notFound = notFound)
    }

    private fun toPageResponse(page: Page<PublishedContent>): PageResponse<ContentResponse> {
        val content = page.content.map { toResponse(it) }
        return PageResponse.of(content, page.number, page.size, page.totalElements)
    }

    private fun toBatchItem(content: PublishedContent, relatedIds: List<String>?): ContentBatchItem {
        val mediaList = absolutizeMedia(jsonConversionService.fromJsonList(content.media))
            ?: emptyList()
        return ContentBatchItem(
            id = content.id.toString(),
            title = content.title,
            description = content.description,
            content = absolutizeHtml(content.contentHtml ?: content.content),
            contentHtml = absolutizeHtml(content.contentHtml),
            contentText = content.contentText,
            previewText = content.previewText,
            contentFormat = content.contentFormat,
            sourceId = content.sourceId.toString(),
            sourceType = content.sourceType,
            sourceSubtype = content.sourceSubtype,
            url = content.url,
            publishedAt = content.publishedAt?.toInstant(),
            authorName = content.authorName,
            media = mediaList,
            metadata = convertToObjectMap(jsonConversionService.fromJson(content.metadata)),
            relatedIds = relatedIds
        )
    }

    private fun toResponse(content: PublishedContent): ContentResponse {
        val mediaList = absolutizeMedia(jsonConversionService.fromJsonList(content.media))
            ?: emptyList()
        return ContentResponse(
            id = content.id.toString(),
            contentId = content.contentId,
            externalId = content.externalId,
            title = content.title,
            description = content.description,
            content = absolutizeHtml(content.contentHtml ?: content.content),
            contentHtml = absolutizeHtml(content.contentHtml),
            contentText = content.contentText,
            previewText = content.previewText,
            contentFormat = content.contentFormat,
            sourceId = content.sourceId.toString(),
            sourceType = SourceType.valueOf(content.sourceType),
            sourceSubtype = content.sourceSubtype,
            url = content.url,
            publishedAt = content.publishedAt?.toInstant(),
            authorId = content.authorId,
            authorName = content.authorName,
            media = mediaList,
            metadata = convertToObjectMap(jsonConversionService.fromJson(content.metadata)),
            dedupArticleId = content.dedupArticleId,
            contentHash = content.contentHash,
            createdAt = content.createdAt?.toInstant(),
            updatedAt = content.updatedAt?.toInstant()
        )
    }

    private fun convertToObjectMap(stringMap: Map<String, String>): Map<String, Any> {
        if (stringMap.isEmpty()) return emptyMap()
        return stringMap.mapValues<String, String, Any> { it.value }
    }
}
