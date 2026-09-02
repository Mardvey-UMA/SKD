package com.contentplatform.feed.application

import com.contentplatform.feed.api.exception.NotFoundException
import com.contentplatform.feed.api.exception.SpaceFeedUnavailableException
import com.contentplatform.feed.application.dto.FeedResponse
import com.contentplatform.feed.db.repository.DislikeRepository
import com.contentplatform.feed.db.repository.SpaceRepository
import com.contentplatform.feed.db.repository.SpaceSourceRepository
import com.contentplatform.feed.infrastructure.cache.ContentCacheService
import com.contentplatform.feed.infrastructure.cache.CursorCodec
import com.contentplatform.feed.infrastructure.cache.FeedCacheService
import com.contentplatform.feed.infrastructure.client.ContentAggregatorClient
import com.contentplatform.feed.infrastructure.client.RecSystemClient
import com.contentplatform.feed.infrastructure.client.RecSystemClientException
import com.contentplatform.feed.infrastructure.client.model.ContentBatchItem
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SpaceFeedService(
    private val spaceRepository: SpaceRepository,
    private val spaceSourceRepository: SpaceSourceRepository,
    private val feedCacheService: FeedCacheService,
    private val contentCacheService: ContentCacheService,
    private val recSystemClient: RecSystemClient,
    private val contentAggregatorClient: ContentAggregatorClient,
    private val dislikeRepository: DislikeRepository,
    private val cursorCodec: CursorCodec,
    @Value("\${feed.page-size:20}") private val defaultPageSize: Int = 20,
    @Value("\${feed.prefetch.batch-size:120}") private val prefetchBatchSize: Int = 120,
    @Value("\${feed.prefetch.trigger-percent:50}") private val prefetchTriggerPercent: Int = 50,
    @Value("\${feed.overfetch-buffer:10}") private val overfetchBuffer: Int = 10,
    @Value("\${feed.max-fetch-iterations:5}") private val maxFetchIterations: Int = 5,
    @Value("\${feed.spaces.max-limit:50}") private val maxLimit: Int = 50
) {

    companion object {
        private val log = LoggerFactory.getLogger(SpaceFeedService::class.java)
    }

    fun getSpaceFeed(userId: UUID, spaceId: UUID, cursor: String?, limit: Int?): FeedResponse {
        val pageSize = resolveLimit(limit)

        spaceRepository.findByIdAndUserId(spaceId, userId)
            ?: throw NotFoundException("Space not found: $spaceId")

        val sourceIds = spaceSourceRepository.findSourceIds(spaceId)
        if (sourceIds.isEmpty()) {
            return FeedResponse(items = emptyList(), cursor = null, hasNext = false)
        }

        val offset = cursorCodec.decode(cursor).toLong()
        val key = cacheKey(spaceId, userId)
        val cacheSize = feedCacheService.getFeedSize(key)

        if (cacheSize == 0L) {
            return fetchAndCache(userId, spaceId, sourceIds, pageSize)
        }

        val (filteredIds, currentOffset, _) = getFilteredPage(key, userId, offset, cacheSize, pageSize)
        if (currentOffset >= (cacheSize * prefetchTriggerPercent / 100)) {
            triggerPrefetch(userId, spaceId, sourceIds)
        }

        val hasNext = currentOffset < cacheSize
        val contents = fetchContentBatch(filteredIds)
        return FeedResponse(
            items = contents,
            cursor = if (hasNext) cursorCodec.encode(currentOffset.toInt()) else null,
            hasNext = hasNext
        )
    }

    private fun fetchAndCache(
        userId: UUID,
        spaceId: UUID,
        sourceIds: List<UUID>,
        pageSize: Int
    ): FeedResponse {
        val key = cacheKey(spaceId, userId)
        val ids = try {
            recSystemClient.getRecommendationsNarrow(userId, prefetchBatchSize, sourceIds)
        } catch (e: RecSystemClientException) {
            log.warn("rec-system NARROW failed for userId={}, spaceId={}: {}", userId, spaceId, e.message)
            throw SpaceFeedUnavailableException(cause = e)
        }

        if (ids.isNullOrEmpty()) {
            return FeedResponse(emptyList(), null, false)
        }

        val stringIds = ids.map { it.toString() }
        feedCacheService.cacheFeed(key, stringIds)
        val cacheSize = stringIds.size.toLong()
        val (filteredIds, nextOffset, _) = getFilteredPage(key, userId, 0L, cacheSize, pageSize)
        val contents = fetchContentBatch(filteredIds)
        val hasNext = nextOffset < cacheSize
        return FeedResponse(
            items = contents,
            cursor = if (hasNext) cursorCodec.encode(nextOffset.toInt()) else null,
            hasNext = hasNext
        )
    }

    private fun getFilteredPage(
        key: String,
        userId: UUID,
        startOffset: Long,
        cacheSize: Long,
        pageSize: Int
    ): Triple<List<String>, Long, Boolean> {
        val result = mutableListOf<String>()
        var currentOffset = startOffset
        var iterations = 0

        while (result.size < pageSize && iterations < maxFetchIterations) {
            val batchSize = minOf(
                (pageSize - result.size + overfetchBuffer).toLong(),
                cacheSize - currentOffset
            )
            if (batchSize <= 0) break

            val batch = feedCacheService.getFeedPage(key, currentOffset, batchSize)
            if (batch.isEmpty()) break

            val uuidBatch = batch.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            val dislikedIds = dislikeRepository.findDislikedContentIds(userId, uuidBatch)
            val filtered = if (dislikedIds.isEmpty()) batch else batch.filter { id ->
                runCatching { UUID.fromString(id) }.getOrNull()?.let { it !in dislikedIds } ?: true
            }

            result.addAll(filtered.take(pageSize - result.size))
            currentOffset += batch.size
            iterations++
        }

        val cacheExhausted = currentOffset >= cacheSize
        return Triple(result, currentOffset, cacheExhausted)
    }

    private fun fetchContentBatch(ids: List<String>): List<ContentBatchItem> {
        if (ids.isEmpty()) return emptyList()
        val cachedItems = mutableMapOf<String, ContentBatchItem>()
        val missingIds = mutableListOf<String>()
        for (id in ids) {
            val cached = contentCacheService.getContent(id)
            if (cached != null) {
                cachedItems[id] = cached
            } else if (!contentCacheService.isNegativeCached(id)) {
                missingIds.add(id)
            }
        }
        val fetchedItems = if (missingIds.isNotEmpty()) {
            val response = contentAggregatorClient.getContentBatch(missingIds)
            response.items.also { items ->
                items.forEach { (id, item) -> contentCacheService.cacheContent(id, item) }
            }
        } else {
            emptyMap()
        }
        return ids.mapNotNull { cachedItems[it] ?: fetchedItems[it] }
    }

    private fun triggerPrefetch(userId: UUID, spaceId: UUID, sourceIds: List<UUID>) {
        val lockKey = "${cacheKey(spaceId, userId)}:prefetch"
        if (feedCacheService.tryAcquirePrefetchLock(lockKey)) {
            try {
                val ids = recSystemClient.getRecommendationsNarrow(userId, prefetchBatchSize, sourceIds)
                if (!ids.isNullOrEmpty()) {
                    feedCacheService.cacheFeed(cacheKey(spaceId, userId), ids.map { it.toString() })
                }
            } catch (e: Exception) {
                log.warn("Space prefetch failed for userId={} spaceId={}: {}", userId, spaceId, e.message)
            }
        }
    }

    private fun resolveLimit(limit: Int?): Int {
        val target = limit ?: defaultPageSize
        if (target < 1 || target > maxLimit) {
            throw IllegalArgumentException("Invalid limit: must be between 1 and $maxLimit")
        }
        return target
    }

    private fun cacheKey(spaceId: UUID, userId: UUID): String =
        "feed:space:$spaceId:user:$userId"
}
