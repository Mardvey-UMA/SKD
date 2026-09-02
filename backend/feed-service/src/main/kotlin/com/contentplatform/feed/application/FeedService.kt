package com.contentplatform.feed.application

import com.contentplatform.feed.application.dto.FeedResponse
import com.contentplatform.feed.db.repository.BlockedSourceRepository
import com.contentplatform.feed.db.repository.DislikeRepository
import com.contentplatform.feed.infrastructure.cache.ContentCacheService
import com.contentplatform.feed.infrastructure.cache.CursorCodec
import com.contentplatform.feed.infrastructure.cache.FeedCacheService
import com.contentplatform.feed.infrastructure.client.ContentAggregatorClient
import com.contentplatform.feed.infrastructure.client.RecSystemClient
import com.contentplatform.feed.infrastructure.client.RecSystemClientException
import com.contentplatform.feed.infrastructure.client.model.ContentBatchItem
import com.contentplatform.feed.infrastructure.client.model.FeedItemDetail
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FeedService(
    private val feedCacheService: FeedCacheService,
    private val contentCacheService: ContentCacheService,
    private val recSystemClient: RecSystemClient,
    private val contentAggregatorClient: ContentAggregatorClient,
    private val dislikeRepository: DislikeRepository,
    private val blockedSourceRepository: BlockedSourceRepository,
    private val cursorCodec: CursorCodec,
    private val meterRegistry: MeterRegistry,
    @Value("\${feed.page-size:20}") private val pageSize: Int = 20,
    @Value("\${feed.prefetch.batch-size:120}") private val prefetchBatchSize: Int = 120,
    @Value("\${feed.prefetch.trigger-percent:50}") private val prefetchTriggerPercent: Int = 50,
    @Value("\${feed.overfetch-buffer:10}") private val overfetchBuffer: Int = 10,
    @Value("\${feed.max-fetch-iterations:5}") private val maxFetchIterations: Int = 5
) {

    companion object {
        private val log = LoggerFactory.getLogger(FeedService::class.java)
        private val FEED_SOURCES = listOf("personalized", "cached", "cold_start", "fallback")
    }

    init {
        FEED_SOURCES.forEach { source ->
            meterRegistry.counter("feed_request_count", "source", source)
        }
    }

    fun getFeed(userId: UUID, cursor: String?, refresh: Boolean): FeedResponse {
        val sample = Timer.start(meterRegistry)
        val response = doGetFeed(userId, cursor, refresh)
        meterRegistry.counter("feed_request_count", "source", response.source).increment()
        sample.stop(
            Timer.builder("feed_request_duration")
                .tag("source", response.source)
                .register(meterRegistry)
        )
        return response
    }

    private fun doGetFeed(userId: UUID, cursor: String?, refresh: Boolean): FeedResponse {
        if (refresh) {
            feedCacheService.deleteFeed(userId)
        }

        val start = System.currentTimeMillis()
        val offset = cursorCodec.decode(cursor)
        val cachedFeed = feedCacheService.getCachedFeed(userId)

        if (cachedFeed == null) {
            return fetchFromRecSystemWithFallback(userId)
        }

        val cacheItems = cachedFeed.items.map { it.toString() }
        val cacheSize = cacheItems.size.toLong()
        val (filteredIds, currentOffset, _) = getFilteredPage(userId, cacheItems, offset.toLong(), cacheSize)

        if (currentOffset >= (cacheSize * prefetchTriggerPercent / 100)) {
            triggerPrefetch(userId)
        }

        val hasNext = currentOffset < cacheSize
        val rawContents = fetchContentBatch(filteredIds)
        val contents = filterBlockedSources(userId, rawContents)
        val latencyMs = (System.currentTimeMillis() - start).toInt()
        return FeedResponse(
            items = contents,
            cursor = if (hasNext) cursorCodec.encode(currentOffset.toInt()) else null,
            hasNext = hasNext,
            source = "cached",
            latencyMs = latencyMs,
            itemsDetailed = cachedFeed.itemsDetailed
        )
    }

    private fun filterBlockedSources(userId: UUID, items: List<ContentBatchItem>): List<ContentBatchItem> {
        if (items.isEmpty()) return items
        val blockedIds = blockedSourceRepository.findBlockedSourceIds(userId)
        if (blockedIds.isEmpty()) return items
        return items.filter { item ->
            val srcId = item.sourceId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            srcId == null || srcId !in blockedIds
        }
    }

    fun getContentById(contentId: String): ContentBatchItem? {
        val cached = contentCacheService.getContent(contentId)
        if (cached != null) return cached

        if (contentCacheService.isNegativeCached(contentId)) return null

        val response = contentAggregatorClient.getContentBatch(listOf(contentId), true, 5)
        val item = response.items[contentId]
        if (item != null) {
            contentCacheService.cacheContent(contentId, item)
            return item
        }

        contentCacheService.setNegativeCache(contentId)
        return null
    }

    private fun getFilteredPage(
        userId: UUID,
        cachedItems: List<String>,
        startOffset: Long,
        cacheSize: Long
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

            val batchStart = currentOffset.toInt().coerceIn(0, cachedItems.size)
            val batchEnd = minOf(batchStart + batchSize.toInt(), cachedItems.size)
            val batch = cachedItems.subList(batchStart, batchEnd)
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

    private fun fetchFromRecSystemWithFallback(userId: UUID): FeedResponse {
        val start = System.currentTimeMillis()
        return try {
            var source = "personalized"
            var itemsDetailed: List<FeedItemDetail>? = null
            var latencyBreakdown: Map<String, Long>? = null
            var profileSnapshot: Map<String, Any>? = null
            var featureFlags: Map<String, Any>? = null

            val ids: List<UUID>? = try {
                val resp = recSystemClient.getRecommendationsWithBreakdown(userId, prefetchBatchSize)
                itemsDetailed = resp?.itemsDetailed
                latencyBreakdown = resp?.latencyBreakdown
                profileSnapshot = resp?.profileSnapshot
                featureFlags = resp?.featureFlags
                resp?.items
            } catch (e: RecSystemClientException) {
                log.warn("rec-system getRecommendationsWithBreakdown failed for userId={}, trying cold-start: {}", userId, e.message)
                source = "cold_start"
                try {
                    recSystemClient.getColdStart(prefetchBatchSize)
                } catch (e2: RecSystemClientException) {
                    log.warn("rec-system cold-start also failed for userId={}, trying Redis trending: {}", userId, e2.message)
                    throw FallbackToTrendingException()
                }
            }

            if (ids.isNullOrEmpty()) {
                return FeedResponse(emptyList(), null, false, source = source, latencyMs = (System.currentTimeMillis() - start).toInt())
            }

            feedCacheService.put(userId, ids, itemsDetailed)

            val stringIds = ids.map { it.toString() }
            val cacheSize = stringIds.size.toLong()
            val (filteredIds, nextOffset, _) = getFilteredPage(userId, stringIds, 0L, cacheSize)
            val contents = filterBlockedSources(userId, fetchContentBatch(filteredIds))
            val hasNext = nextOffset < cacheSize
            val latencyMs = (System.currentTimeMillis() - start).toInt()

            FeedResponse(
                items = contents,
                cursor = if (hasNext) cursorCodec.encode(nextOffset.toInt()) else null,
                hasNext = hasNext,
                source = source,
                latencyMs = latencyMs,
                latencyBreakdown = latencyBreakdown,
                itemsDetailed = itemsDetailed,
                profileSnapshot = profileSnapshot,
                featureFlags = featureFlags,
                countRequested = prefetchBatchSize
            )
        } catch (e: FallbackToTrendingException) {
            val trendingIds = contentCacheService.getColdStartTrending()
            if (!trendingIds.isNullOrEmpty()) {
                feedCacheService.cacheFeed(userId, trendingIds)
                val cacheSize = trendingIds.size.toLong()
                val (filteredIds, _, _) = getFilteredPage(userId, trendingIds, 0L, cacheSize)
                val contents = filterBlockedSources(userId, fetchContentBatch(filteredIds))
                FeedResponse(
                    items = contents,
                    cursor = null,
                    hasNext = false,
                    source = "fallback",
                    latencyMs = (System.currentTimeMillis() - start).toInt(),
                    countRequested = trendingIds.size
                )
            } else {
                log.warn("Entire fallback chain failed for userId={}, returning empty feed", userId)
                FeedResponse(
                    emptyList(),
                    null,
                    false,
                    "Feed temporarily unavailable",
                    source = "fallback",
                    latencyMs = (System.currentTimeMillis() - start).toInt()
                )
            }
        }
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

    private fun triggerPrefetch(userId: UUID) {
        if (feedCacheService.tryAcquirePrefetchLock(userId)) {
            try {
                val ids = recSystemClient.getRecommendations(userId, prefetchBatchSize)
                if (!ids.isNullOrEmpty()) {
                    feedCacheService.cacheFeed(userId, ids.map { it.toString() })
                }
            } catch (e: Exception) {
                log.warn("Prefetch failed for userId={}: {}", userId, e.message)
            }
        }
    }

    private class FallbackToTrendingException : RuntimeException()
}
