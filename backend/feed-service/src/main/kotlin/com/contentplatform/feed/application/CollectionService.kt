package com.contentplatform.feed.application

import com.contentplatform.feed.application.dto.CollectionPageResponse
import com.contentplatform.feed.application.dto.ContentStatusResponse
import com.contentplatform.feed.application.exception.AlreadyExistsException
import com.contentplatform.feed.db.repository.BookmarkRepository
import com.contentplatform.feed.db.repository.DislikeRepository
import com.contentplatform.feed.db.repository.LikeRepository
import com.contentplatform.feed.infrastructure.cache.CursorCodec
import com.contentplatform.feed.infrastructure.cache.FeedCacheService
import com.contentplatform.feed.infrastructure.client.ContentAggregatorClient
import com.contentplatform.feed.infrastructure.client.model.ContentBatchItem
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CollectionService(
    private val bookmarkRepository: BookmarkRepository,
    private val likeRepository: LikeRepository,
    private val dislikeRepository: DislikeRepository,
    private val feedCacheService: FeedCacheService,
    private val contentAggregatorClient: ContentAggregatorClient? = null,
    private val cursorCodec: CursorCodec? = null,
    @Value("\${feed.page-size:20}") private val pageSize: Int = 20
) {

    companion object {
        private val log = LoggerFactory.getLogger(CollectionService::class.java)
    }

    fun addBookmark(userId: UUID, contentId: UUID) {
        try {
            bookmarkRepository.insert(userId, contentId)
        } catch (e: DuplicateKeyException) {
            throw AlreadyExistsException("Bookmark already exists for contentId=$contentId")
        }
    }

    fun removeBookmark(userId: UUID, contentId: UUID) {
        bookmarkRepository.deleteByUserIdAndContentId(userId, contentId)
    }

    fun addLike(userId: UUID, contentId: UUID) {
        try {
            likeRepository.insert(userId, contentId)
        } catch (e: DuplicateKeyException) {
            throw AlreadyExistsException("Like already exists for contentId=$contentId")
        }
        dislikeRepository.deleteByUserIdAndContentId(userId, contentId)
    }

    fun removeLike(userId: UUID, contentId: UUID) {
        likeRepository.deleteByUserIdAndContentId(userId, contentId)
    }

    fun addDislike(userId: UUID, contentId: UUID) {
        try {
            dislikeRepository.insert(userId, contentId)
        } catch (e: DuplicateKeyException) {
            throw AlreadyExistsException("Dislike already exists for contentId=$contentId")
        }
        likeRepository.deleteByUserIdAndContentId(userId, contentId)
        feedCacheService.deleteFeed(userId)
    }

    fun removeDislike(userId: UUID, contentId: UUID) {
        dislikeRepository.deleteByUserIdAndContentId(userId, contentId)
    }

    fun getContentStatus(userId: UUID, contentId: UUID): ContentStatusResponse = ContentStatusResponse(
        liked = likeRepository.existsByUserIdAndContentId(userId, contentId),
        disliked = dislikeRepository.existsByUserIdAndContentId(userId, contentId),
        bookmarked = bookmarkRepository.existsByUserIdAndContentId(userId, contentId)
    )

    fun getBookmarks(userId: UUID, cursor: String?): CollectionPageResponse {
        val offset = cursorCodec?.decode(cursor) ?: 0
        val entities = bookmarkRepository.findByUserId(userId, pageSize + 1, offset)
        return buildPageResponse(entities.map { it.contentId.toString() }, offset)
    }

    fun getLikes(userId: UUID, cursor: String?): CollectionPageResponse {
        val offset = cursorCodec?.decode(cursor) ?: 0
        val entities = likeRepository.findByUserId(userId, pageSize + 1, offset)
        return buildPageResponse(entities.map { it.contentId.toString() }, offset)
    }

    fun getDislikes(userId: UUID, cursor: String?): CollectionPageResponse {
        val offset = cursorCodec?.decode(cursor) ?: 0
        val entities = dislikeRepository.findByUserId(userId, pageSize + 1, offset)
        return buildPageResponse(entities.map { it.contentId.toString() }, offset)
    }

    private fun buildPageResponse(ids: List<String>, offset: Int): CollectionPageResponse {
        val hasNext = ids.size > pageSize
        val pageIds = if (hasNext) ids.dropLast(1) else ids
        val items = fetchContentItems(pageIds)
        val nextCursor = if (hasNext) cursorCodec?.encode(offset + pageSize) else null
        return CollectionPageResponse(items = items, cursor = nextCursor, hasNext = hasNext)
    }

    private fun fetchContentItems(ids: List<String>): List<ContentBatchItem> {
        if (ids.isEmpty() || contentAggregatorClient == null) return emptyList()
        return try {
            val response = contentAggregatorClient.getContentBatch(ids)
            ids.mapNotNull { response.items[it] }
        } catch (e: Exception) {
            log.warn("Failed to fetch content items for collection: {}", e.message)
            emptyList()
        }
    }
}
