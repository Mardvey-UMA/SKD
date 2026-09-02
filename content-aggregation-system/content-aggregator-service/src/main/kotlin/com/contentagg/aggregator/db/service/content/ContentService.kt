package com.contentagg.aggregator.db.service.content

import com.contentagg.aggregator.db.repository.aggregatedcontent.model.SourceType
import com.contentagg.aggregator.db.repository.publishedcontent.PublishedContentRepository
import com.contentagg.aggregator.db.repository.publishedcontent.model.PublishedContent
import com.contentagg.aggregator.exception.ContentNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class ContentService(
    private val contentRepository: PublishedContentRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(ContentService::class.java)
    }

    fun searchContent(
        sourceType: SourceType?,
        sourceId: String?,
        search: String?,
        fromDate: Instant?,
        toDate: Instant?,
        page: Int,
        size: Int,
        sortBy: String?,
        sortDirection: String?
    ): Page<PublishedContent> {
        log.debug("Searching content: sourceType={}, sourceId={}, page={}, size={}", sourceType, sourceId, page, size)

        val validatedPage = maxOf(0, page)
        val validatedSize = minOf(100, maxOf(1, size))
        val offset = validatedPage.toLong() * validatedSize
        val pageable = createPageable(validatedPage, validatedSize, sortBy, sortDirection)
        val searchTerm = search?.takeIf { it.isNotBlank() }

        return when {
            sourceType != null && !searchTerm.isNullOrBlank() -> {
                val content = contentRepository.searchBySourceType(sourceType.name, searchTerm, validatedSize, offset)
                val total = contentRepository.countBySourceTypeSearch(sourceType.name, searchTerm)
                PageImpl(content, pageable, total)
            }
            sourceType != null -> {
                val content = contentRepository.findBySourceTypeOrdered(sourceType.name, validatedSize, offset)
                val total = contentRepository.countBySourceType(sourceType.name)
                PageImpl(content, pageable, total)
            }
            !searchTerm.isNullOrBlank() -> {
                val content = contentRepository.searchByText(searchTerm, validatedSize, offset)
                val total = contentRepository.countByText(searchTerm)
                PageImpl(content, pageable, total)
            }
            else -> {
                val content = contentRepository.findAllOrderByPublishedAtDescNullsLast(validatedSize, offset)
                val total = contentRepository.countAll()
                PageImpl(content, pageable, total)
            }
        }
    }

    fun getContentById(id: UUID): PublishedContent {
        log.debug("Fetching content by id: {}", id)
        return contentRepository.findById(id).orElseThrow { ContentNotFoundException(id) }
    }

    fun getLatestContent(page: Int, size: Int): Page<PublishedContent> {
        log.debug("Fetching latest content: page={}, size={}", page, size)
        val validatedPage = maxOf(0, page)
        val validatedSize = minOf(100, maxOf(1, size))
        val offset = validatedPage.toLong() * validatedSize
        val pageable = PageRequest.of(validatedPage, validatedSize)
        val content = contentRepository.findAllOrderByPublishedAtDescNullsLast(validatedSize, offset)
        val total = contentRepository.countAll()
        return PageImpl(content, pageable, total)
    }

    fun getContentBySourceType(sourceType: SourceType, page: Int, size: Int): Page<PublishedContent> {
        log.debug("Fetching content by source type: {}", sourceType)
        val validatedPage = maxOf(0, page)
        val validatedSize = minOf(100, maxOf(1, size))
        val offset = validatedPage.toLong() * validatedSize
        val pageable = PageRequest.of(validatedPage, validatedSize)
        val content = contentRepository.findBySourceTypeOrdered(sourceType.name, validatedSize, offset)
        val total = contentRepository.countBySourceType(sourceType.name)
        return PageImpl(content, pageable, total)
    }

    fun findByIds(ids: List<UUID>): List<PublishedContent> {
        log.debug("Fetching content by ids: count={}", ids.size)
        if (ids.isEmpty()) return emptyList()
        return contentRepository.findAllByIdIn(ids.toTypedArray())
    }

    fun getTotalContentCount(): Long = contentRepository.count()

    fun getContentCountBySourceType(sourceType: SourceType): Long =
        contentRepository.countBySourceType(sourceType.name)

    private fun createPageable(page: Int, size: Int, sortBy: String?, sortDirection: String?): Pageable {
        val sortField = sortBy ?: "publishedAt"
        val direction = if ("asc".equals(sortDirection, ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
        return PageRequest.of(page, size, Sort.by(direction, sortField))
    }
}
