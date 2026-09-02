package com.contentplatform.feed.unit.service

import com.contentplatform.feed.application.FeedService
import com.contentplatform.feed.application.dto.FeedResponse
import com.contentplatform.feed.db.repository.BlockedSourceRepository
import com.contentplatform.feed.db.repository.DislikeRepository
import com.contentplatform.feed.infrastructure.cache.CachedFeed
import com.contentplatform.feed.infrastructure.cache.ContentCacheService
import com.contentplatform.feed.infrastructure.cache.CursorCodec
import com.contentplatform.feed.infrastructure.cache.FeedCacheService
import com.contentplatform.feed.infrastructure.client.ContentAggregatorClient
import com.contentplatform.feed.infrastructure.client.ContentAggregatorClientException
import com.contentplatform.feed.infrastructure.client.RecSystemClient
import com.contentplatform.feed.infrastructure.client.RecSystemClientException
import com.contentplatform.feed.infrastructure.client.model.ContentBatchItem
import com.contentplatform.feed.infrastructure.client.model.ContentBatchResponse
import com.contentplatform.feed.infrastructure.client.model.ExtendedRecommendationsResponse
import com.contentplatform.feed.infrastructure.client.model.FeedItemDetail
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class FeedServiceTest {

    private val feedCacheService = mockk<FeedCacheService>()
    private val contentCacheService = mockk<ContentCacheService>()
    private val recSystemClient = mockk<RecSystemClient>()
    private val contentAggregatorClient = mockk<ContentAggregatorClient>()
    private val dislikeRepository = mockk<DislikeRepository>(relaxed = true)
    private val blockedSourceRepository = mockk<BlockedSourceRepository>(relaxed = true)
    private val cursorCodec = CursorCodec()

    private val feedService = FeedService(
        feedCacheService = feedCacheService,
        contentCacheService = contentCacheService,
        recSystemClient = recSystemClient,
        contentAggregatorClient = contentAggregatorClient,
        dislikeRepository = dislikeRepository,
        blockedSourceRepository = blockedSourceRepository,
        cursorCodec = cursorCodec,
        meterRegistry = SimpleMeterRegistry(),
        pageSize = 20,
        prefetchBatchSize = 120,
        prefetchTriggerPercent = 50,
        overfetchBuffer = 10,
        maxFetchIterations = 5
    )

    companion object {
        fun createContentBatchItem(id: String = UUID.randomUUID().toString()): ContentBatchItem =
            ContentBatchItem(
                id = id,
                title = "Test Article $id",
                description = "Description for $id",
                content = "Full content for $id",
                url = "https://example.com/$id"
            )

        fun makeExtended(userId: UUID, ids: List<UUID>) =
            ExtendedRecommendationsResponse(userId = userId, items = ids, count = ids.size)

        fun cachedFeed(uuids: List<UUID>, detailed: List<FeedItemDetail>? = null) =
            CachedFeed(items = uuids, itemsDetailed = detailed, cachedAt = Instant.now())
    }

    @Nested
    inner class `getFeed cache miss` {

        @Test
        fun `should fetch from rec-system and cache when cache is empty`() {
            val userId = UUID.randomUUID()
            val recIds = (1..40).map { UUID.randomUUID() }
            val items = recIds.take(20).map { it.toString() }.associateWith { createContentBatchItem(it) }

            every { feedCacheService.getCachedFeed(userId) } returns null
            every { recSystemClient.getRecommendationsWithBreakdown(userId, 120) } returns makeExtended(userId, recIds)
            every { feedCacheService.put(userId, any(), any()) } just runs
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, null, false)

            assertThat(result.items).hasSize(20)
            assertThat(result.hasNext).isTrue()
            verify(exactly = 1) { recSystemClient.getRecommendationsWithBreakdown(userId, 120) }
            verify(exactly = 1) { feedCacheService.put(userId, any(), any()) }
        }

        @Test
        fun `should return cursor for next page when more items available`() {
            val userId = UUID.randomUUID()
            val recIds = (1..40).map { UUID.randomUUID() }
            val items = recIds.take(20).map { it.toString() }.associateWith { createContentBatchItem(it) }

            every { feedCacheService.getCachedFeed(userId) } returns null
            every { recSystemClient.getRecommendationsWithBreakdown(userId, 120) } returns makeExtended(userId, recIds)
            every { feedCacheService.put(userId, any(), any()) } just runs
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, null, false)

            assertThat(result.cursor).isNotNull()
            val nextOffset = cursorCodec.decode(result.cursor)
            // Cursor must encode actual consumed offset (30), not offset+pageSize (20)
            assertThat(nextOffset).isEqualTo(30)
        }
    }

    @Nested
    inner class `getFeed cache hit` {

        @Test
        fun `should return page from cache without calling rec-system`() {
            val userId = UUID.randomUUID()
            val uuidIds = (1..40).map { UUID.randomUUID() }
            val pageIds = uuidIds.subList(20, 40).map { it.toString() }
            val items = pageIds.associateWith { createContentBatchItem(it) }
            val cursor = cursorCodec.encode(20)

            every { feedCacheService.getCachedFeed(userId) } returns cachedFeed(uuidIds)
            every { feedCacheService.tryAcquirePrefetchLock(userId) } returns false
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, cursor, false)

            assertThat(result.items).hasSize(20)
            verify(exactly = 0) { recSystemClient.getRecommendationsWithBreakdown(any(), any()) }
        }

        @Test
        fun `should return hasNext false when no more items after current page`() {
            val userId = UUID.randomUUID()
            val uuidIds = (1..25).map { UUID.randomUUID() }
            val pageIds = uuidIds.subList(20, 25).map { it.toString() }
            val items = pageIds.associateWith { createContentBatchItem(it) }
            val cursor = cursorCodec.encode(20)

            every { feedCacheService.getCachedFeed(userId) } returns cachedFeed(uuidIds)
            every { feedCacheService.tryAcquirePrefetchLock(userId) } returns false
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, cursor, false)

            assertThat(result.items).hasSize(5)
            assertThat(result.hasNext).isFalse()
            assertThat(result.cursor).isNull()
        }

        @Test
        fun `should return itemsDetailed from cached feed on cache hit`() {
            val userId = UUID.randomUUID()
            val uuidIds = (1..5).map { UUID.randomUUID() }
            val detailed = uuidIds.map { id ->
                FeedItemDetail(contentId = id, finalScore = 0.8, scoringComponents = mapOf("recency" to 0.5))
            }
            val items = uuidIds.map { it.toString() }.associateWith { createContentBatchItem(it) }

            every { feedCacheService.getCachedFeed(userId) } returns cachedFeed(uuidIds, detailed)
            every { feedCacheService.tryAcquirePrefetchLock(userId) } returns false
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, null, false)

            assertThat(result.source).isEqualTo("cached")
            assertThat(result.itemsDetailed).isNotNull
            assertThat(result.itemsDetailed).hasSize(5)
            assertThat(result.itemsDetailed!![0].contentId).isEqualTo(uuidIds[0])
            assertThat(result.itemsDetailed[0].scoringComponents).containsEntry("recency", 0.5)
        }
    }

    @Nested
    inner class `getFeed refresh` {

        @Test
        fun `should delete cache and fetch fresh data when refresh is true`() {
            val userId = UUID.randomUUID()
            val recIds = (1..20).map { UUID.randomUUID() }
            val items = recIds.map { it.toString() }.associateWith { createContentBatchItem(it.toString()) }

            every { feedCacheService.deleteFeed(userId) } just runs
            every { feedCacheService.getCachedFeed(userId) } returns null
            every { recSystemClient.getRecommendationsWithBreakdown(userId, 120) } returns makeExtended(userId, recIds)
            every { feedCacheService.put(userId, any(), any()) } just runs
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, null, true)

            assertThat(result.items).isNotEmpty()
            verify(exactly = 1) { feedCacheService.deleteFeed(userId) }
            verify(exactly = 1) { recSystemClient.getRecommendationsWithBreakdown(userId, 120) }
        }
    }

    @Nested
    inner class `prefetch trigger` {

        @Test
        fun `should trigger prefetch when cursor reaches 50 percent of cached feed`() {
            val userId = UUID.randomUUID()
            val uuidIds = (1..120).map { UUID.randomUUID() }
            val offset = 60
            val cursor = cursorCodec.encode(offset)
            val pageIds = uuidIds.subList(60, 80).map { it.toString() }
            val items = pageIds.associateWith { createContentBatchItem(it) }

            // offset=60, cacheSize=120, batchSize=min(30, 60)=30, batch=items[60..90]
            // take(20)=20, currentOffset=90 ≥ 60 → prefetch triggered
            every { feedCacheService.getCachedFeed(userId) } returns cachedFeed(uuidIds)
            every { feedCacheService.tryAcquirePrefetchLock(userId) } returns true
            every { recSystemClient.getRecommendations(userId, 120) } returns (1..20).map { UUID.randomUUID() }
            every { feedCacheService.cacheFeed(any<UUID>(), any()) } just runs
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            feedService.getFeed(userId, cursor, false)

            verify(exactly = 1) { feedCacheService.tryAcquirePrefetchLock(userId) }
        }

        @Test
        fun `should not trigger prefetch when below 50 percent threshold`() {
            val userId = UUID.randomUUID()
            val uuidIds = (1..120).map { UUID.randomUUID() }
            val offset = 20
            val cursor = cursorCodec.encode(offset)
            val pageIds = uuidIds.subList(20, 40).map { it.toString() }
            val items = pageIds.associateWith { createContentBatchItem(it) }

            // offset=20, batchSize=min(30, 100)=30, batch=items[20..50]
            // take(20)=20, currentOffset=50 < 60 → no prefetch
            every { feedCacheService.getCachedFeed(userId) } returns cachedFeed(uuidIds)
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            feedService.getFeed(userId, cursor, false)

            verify(exactly = 0) { feedCacheService.tryAcquirePrefetchLock(any<UUID>()) }
        }
    }

    @Nested
    inner class `fallback chain` {

        @Test
        fun `should try cold-start when rec-system throws exception`() {
            val userId = UUID.randomUUID()
            val coldStartIds = (1..10).map { UUID.randomUUID() }
            val stringIds = coldStartIds.map { it.toString() }
            val items = stringIds.associateWith { createContentBatchItem(it) }

            every { feedCacheService.getCachedFeed(userId) } returns null
            every { recSystemClient.getRecommendationsWithBreakdown(userId, 120) } throws RecSystemClientException("5xx error")
            every { recSystemClient.getColdStart(120) } returns coldStartIds
            every { feedCacheService.put(userId, any(), any()) } just runs
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, null, false)

            assertThat(result.items).isNotEmpty()
            verify(exactly = 1) { recSystemClient.getColdStart(120) }
        }

        @Test
        fun `should try Redis trending when both rec-system and cold-start fail`() {
            val userId = UUID.randomUUID()
            val trendingIds = listOf("trending-1", "trending-2", "trending-3")
            val items = trendingIds.associateWith { createContentBatchItem(it) }

            every { feedCacheService.getCachedFeed(userId) } returns null
            every { recSystemClient.getRecommendationsWithBreakdown(userId, 120) } throws RecSystemClientException("5xx")
            every { recSystemClient.getColdStart(120) } throws RecSystemClientException("cold-start also down")
            every { contentCacheService.getColdStartTrending() } returns trendingIds
            every { feedCacheService.cacheFeed(userId, trendingIds) } just runs
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, null, false)

            assertThat(result.items).hasSize(3)
            verify(exactly = 1) { contentCacheService.getColdStartTrending() }
        }

        @Test
        fun `should return empty feed with message when entire fallback chain fails`() {
            val userId = UUID.randomUUID()

            every { feedCacheService.getCachedFeed(userId) } returns null
            every { recSystemClient.getRecommendationsWithBreakdown(userId, 120) } throws RecSystemClientException("5xx")
            every { recSystemClient.getColdStart(120) } throws RecSystemClientException("cold-start also down")
            every { contentCacheService.getColdStartTrending() } returns null

            val result = feedService.getFeed(userId, null, false)

            assertThat(result.items).isEmpty()
            assertThat(result.hasNext).isFalse()
            assertThat(result.cursor).isNull()
            assertThat(result.message).isNotNull()
        }
    }

    @Nested
    inner class `getContentById cache hit` {

        @Test
        fun `should return content from cache when available`() {
            val contentId = UUID.randomUUID().toString()
            val cachedItem = createContentBatchItem(contentId)

            every { contentCacheService.getContent(contentId) } returns cachedItem

            val result = feedService.getContentById(contentId)

            assertThat(result).isNotNull()
            assertThat(result!!.id).isEqualTo(contentId)
            verify(exactly = 0) { contentAggregatorClient.getContentBatch(any(), any(), any()) }
        }
    }

    @Nested
    inner class `getContentById cache miss` {

        @Test
        fun `should fetch from content-aggregator and cache on cache miss`() {
            val contentId = UUID.randomUUID().toString()
            val item = createContentBatchItem(contentId)

            every { contentCacheService.getContent(contentId) } returns null
            every { contentCacheService.isNegativeCached(contentId) } returns false
            every { contentAggregatorClient.getContentBatch(listOf(contentId), true, 5) } returns ContentBatchResponse(
                items = mapOf(contentId to item)
            )
            every { contentCacheService.cacheContent(contentId, item) } just runs

            val result = feedService.getContentById(contentId)

            assertThat(result).isNotNull()
            assertThat(result!!.id).isEqualTo(contentId)
            verify(exactly = 1) { contentCacheService.cacheContent(contentId, item) }
        }
    }

    @Nested
    inner class `getContentById negative cache` {

        @Test
        fun `should return null when content is negative cached`() {
            val contentId = UUID.randomUUID().toString()

            every { contentCacheService.getContent(contentId) } returns null
            every { contentCacheService.isNegativeCached(contentId) } returns true

            val result = feedService.getContentById(contentId)

            assertThat(result).isNull()
            verify(exactly = 0) { contentAggregatorClient.getContentBatch(any(), any(), any()) }
        }

        @Test
        fun `should set negative cache when content-aggregator returns not found`() {
            val contentId = UUID.randomUUID().toString()

            every { contentCacheService.getContent(contentId) } returns null
            every { contentCacheService.isNegativeCached(contentId) } returns false
            every { contentAggregatorClient.getContentBatch(listOf(contentId), true, 5) } returns ContentBatchResponse(
                items = emptyMap(),
                notFound = listOf(contentId)
            )
            every { contentCacheService.setNegativeCache(contentId) } just runs

            val result = feedService.getContentById(contentId)

            assertThat(result).isNull()
            verify(exactly = 1) { contentCacheService.setNegativeCache(contentId) }
        }
    }

    @Nested
    inner class `getFeed dislike filter` {

        @Test
        fun `should filter disliked content from cached page IDs`() {
            val userId = UUID.randomUUID()
            val id1 = UUID.randomUUID()
            val id2 = UUID.randomUUID()
            val id3 = UUID.randomUUID()
            val visibleIds = listOf(id1.toString(), id3.toString())
            val items = visibleIds.associateWith { createContentBatchItem(it) }

            every { feedCacheService.getCachedFeed(userId) } returns cachedFeed(listOf(id1, id2, id3))
            every { feedCacheService.tryAcquirePrefetchLock(userId) } returns false
            every { dislikeRepository.findDislikedContentIds(userId, any()) } returns setOf(id2)
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, null, false)

            assertThat(result.items).hasSize(2)
            assertThat(result.items.map { it.id }).doesNotContain(id2.toString())
            assertThat(result.items.map { it.id }).containsExactlyInAnyOrder(id1.toString(), id3.toString())
        }

        @Test
        fun `should return all items when no dislikes exist`() {
            val userId = UUID.randomUUID()
            val ids = (1..3).map { UUID.randomUUID() }
            val items = ids.map { it.toString() }.associateWith { createContentBatchItem(it) }

            every { feedCacheService.getCachedFeed(userId) } returns cachedFeed(ids)
            every { feedCacheService.tryAcquirePrefetchLock(userId) } returns false
            every { dislikeRepository.findDislikedContentIds(userId, any()) } returns emptySet()
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, null, false)

            assertThat(result.items).hasSize(3)
        }
    }

    // ============================================================
    // Over-fetch loop, correct cursor, dislike filtering
    // ============================================================

    @Nested
    inner class `over-fetch loop` {

        @Test
        fun `should return full pageSize after filtering dislikes from first batch`() {
            val userId = UUID.randomUUID()
            val allIds = (1..60).map { UUID.randomUUID() }
            val dislikedSet = allIds.take(5).toSet()
            // batchSize=min(30,60)=30; 5 disliked → 25 remain → take(20)=20
            val visibleIds = allIds.drop(5).take(20).map { it.toString() }
            val items = visibleIds.associateWith { createContentBatchItem(it) }

            every { feedCacheService.getCachedFeed(userId) } returns cachedFeed(allIds)
            every { feedCacheService.tryAcquirePrefetchLock(userId) } returns false
            every { dislikeRepository.findDislikedContentIds(userId, any()) } returns dislikedSet
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, null, false)

            assertThat(result.items).hasSize(20)
            assertThat(result.items.map { it.id }).noneMatch { id ->
                dislikedSet.any { it.toString() == id }
            }
        }

        @Test
        fun `should iterate multiple times to fill page when many items disliked in first batch`() {
            val userId = UUID.randomUUID()
            val allIds = (1..100).map { UUID.randomUUID() }
            val firstDislikedSet = allIds.take(20).toSet()
            // Iter1: batch[0..30], 20 disliked → 10; Iter2: batch[30..50], 0 disliked → take(10)=10
            val expectedItems = allIds.subList(20, 40).map { it.toString() }.associateWith { createContentBatchItem(it) }

            every { feedCacheService.getCachedFeed(userId) } returns cachedFeed(allIds)
            every { feedCacheService.tryAcquirePrefetchLock(userId) } returns false
            every { dislikeRepository.findDislikedContentIds(userId, any()) } answers {
                val requested = secondArg<Collection<UUID>>()
                requested.filter { it in firstDislikedSet }.toSet()
            }
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = expectedItems)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, null, false)

            assertThat(result.items).hasSize(20)
        }

        @Test
        fun `should stop after maxFetchIterations even when page is not full`() {
            val limitedService = FeedService(
                feedCacheService = feedCacheService,
                contentCacheService = contentCacheService,
                recSystemClient = recSystemClient,
                contentAggregatorClient = contentAggregatorClient,
                dislikeRepository = dislikeRepository,
                blockedSourceRepository = blockedSourceRepository,
                cursorCodec = cursorCodec,
                meterRegistry = SimpleMeterRegistry(),
                pageSize = 20,
                prefetchBatchSize = 120,
                prefetchTriggerPercent = 50,
                overfetchBuffer = 10,
                maxFetchIterations = 2
            )

            val userId = UUID.randomUUID()
            val allIds = (1..200).map { UUID.randomUUID() }
            every { feedCacheService.getCachedFeed(userId) } returns cachedFeed(allIds)
            every { dislikeRepository.findDislikedContentIds(userId, any()) } answers {
                secondArg<Collection<UUID>>().toSet()
            }

            val result = limitedService.getFeed(userId, null, false)

            assertThat(result.items).isEmpty()
        }

        @Test
        fun `cursor encodes actual consumed cache offset not offset plus pageSize`() {
            val userId = UUID.randomUUID()
            val allIds = (1..80).map { UUID.randomUUID() }
            // batchSize=min(30,80)=30, take(20)=20, currentOffset=30 → cursor=encode(30)
            val items = allIds.take(20).map { it.toString() }.associateWith { createContentBatchItem(it) }

            every { feedCacheService.getCachedFeed(userId) } returns cachedFeed(allIds)
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, null, false)

            assertThat(result.cursor).isNotNull()
            val decodedOffset = cursorCodec.decode(result.cursor)
            // Must be 30 (actual cache entries consumed), not 20 (offset+pageSize)
            assertThat(decodedOffset).isEqualTo(30)
        }

        @Test
        fun `when cacheSize is 0 with non-zero cursor offset should reset and fetch from rec-system`() {
            val userId = UUID.randomUUID()
            val cursor = cursorCodec.encode(40)
            val recIds = (1..20).map { UUID.randomUUID() }
            val items = recIds.map { it.toString() }.associateWith { createContentBatchItem(it.toString()) }

            every { feedCacheService.getCachedFeed(userId) } returns null
            every { recSystemClient.getRecommendationsWithBreakdown(userId, 120) } returns makeExtended(userId, recIds)
            every { feedCacheService.put(userId, any(), any()) } just runs
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, cursor, false)

            assertThat(result.items).isNotEmpty()
            verify(exactly = 1) { recSystemClient.getRecommendationsWithBreakdown(userId, 120) }
        }

        @Test
        fun `should return fewer items when cache exhausted before filling full page`() {
            val userId = UUID.randomUUID()
            val allIds = (1..12).map { UUID.randomUUID() }
            val dislikedSet = allIds.take(2).toSet()
            val visibleItems = allIds.drop(2).map { it.toString() }.associateWith { createContentBatchItem(it) }

            every { feedCacheService.getCachedFeed(userId) } returns cachedFeed(allIds)
            every { feedCacheService.tryAcquirePrefetchLock(userId) } returns false
            every { dislikeRepository.findDislikedContentIds(userId, any()) } returns dislikedSet
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = visibleItems)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, null, false)

            assertThat(result.items).hasSize(10)
            assertThat(result.hasNext).isFalse()
            assertThat(result.cursor).isNull()
        }

        @Test
        fun `fetchFromRecSystemWithFallback should apply dislike filtering to first page`() {
            val userId = UUID.randomUUID()
            val recUuids = (1..40).map { UUID.randomUUID() }
            val dislikedSet = recUuids.take(5).toSet()
            val visibleIds = recUuids.drop(5).take(20).map { it.toString() }
            val items = visibleIds.associateWith { createContentBatchItem(it) }

            every { feedCacheService.getCachedFeed(userId) } returns null
            every { recSystemClient.getRecommendationsWithBreakdown(userId, 120) } returns makeExtended(userId, recUuids)
            every { feedCacheService.put(userId, any(), any()) } just runs
            every { dislikeRepository.findDislikedContentIds(userId, any()) } returns dislikedSet
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, null, false)

            assertThat(result.items).hasSize(20)
            assertThat(result.items.map { it.id }).noneMatch { id ->
                dislikedSet.any { it.toString() == id }
            }
        }

        @Test
        fun `cursor is null when over-fetch loop exhausts entire cache`() {
            val userId = UUID.randomUUID()
            val allIds = (1..20).map { UUID.randomUUID() }
            val items = allIds.map { it.toString() }.associateWith { createContentBatchItem(it.toString()) }

            every { feedCacheService.getCachedFeed(userId) } returns cachedFeed(allIds)
            every { feedCacheService.tryAcquirePrefetchLock(userId) } returns false
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, null, false)

            assertThat(result.cursor).isNull()
            assertThat(result.hasNext).isFalse()
            assertThat(result.items).hasSize(20)
        }

        @Test
        fun `prefetch is triggered based on actual currentOffset after over-fetch loop`() {
            val userId = UUID.randomUUID()
            val allIds = (1..120).map { UUID.randomUUID() }
            val cursor = cursorCodec.encode(30)
            // offset=30, batchSize=min(30,90)=30, items[30..60], take(20), currentOffset=60 ≥ 60 → trigger
            val pageIds = allIds.subList(30, 50).map { it.toString() }
            val items = pageIds.associateWith { createContentBatchItem(it) }

            every { feedCacheService.getCachedFeed(userId) } returns cachedFeed(allIds)
            every { feedCacheService.tryAcquirePrefetchLock(userId) } returns false
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            feedService.getFeed(userId, cursor, false)

            // currentOffset=60 ≥ 60 → prefetch triggered
            verify(exactly = 1) { feedCacheService.tryAcquirePrefetchLock(userId) }
        }
    }

    @Nested
    inner class `breakdown propagation` {

        @Test
        fun `getFeed should propagate itemsDetailed and featureFlags into FeedResponse on personalized path`() {
            val userId = UUID.randomUUID()
            val contentId1 = UUID.randomUUID()
            val contentId2 = UUID.randomUUID()
            val detail1 = FeedItemDetail(
                contentId = contentId1,
                rawContentId = UUID.randomUUID(),
                finalScore = 0.742,
                scoringComponents = mapOf("topic_match" to 0.42, "embedding_sim" to 0.31,
                    "entity_match" to 0.05, "sentiment_match" to 0.00,
                    "freshness" to 0.15, "format_match" to 0.02),
                rerankScore = null
            )
            val detail2 = FeedItemDetail(contentId = contentId2, finalScore = 0.55)
            val extResp = ExtendedRecommendationsResponse(
                userId = userId,
                items = listOf(contentId1, contentId2),
                count = 2,
                itemsDetailed = listOf(detail1, detail2),
                latencyBreakdown = mapOf("rec_system_ms" to 95L, "total_ms" to 143L),
                featureFlags = mapOf("live_profile" to false, "rerank" to false)
            )
            val items = listOf(contentId1.toString(), contentId2.toString()).associateWith { createContentBatchItem(it) }

            every { feedCacheService.getCachedFeed(userId) } returns null
            every { recSystemClient.getRecommendationsWithBreakdown(userId, 120) } returns extResp
            every { feedCacheService.put(userId, any(), any()) } just runs
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, null, false)

            assertThat(result.itemsDetailed).isNotNull
            assertThat(result.itemsDetailed!!).hasSize(2)
            assertThat(result.itemsDetailed!![0].contentId).isEqualTo(contentId1)
            assertThat(result.latencyBreakdown).isEqualTo(mapOf("rec_system_ms" to 95L, "total_ms" to 143L))
            assertThat(result.featureFlags).isNotNull
            assertThat(result.featureFlags!!["live_profile"]).isEqualTo(false)
            assertThat(result.countRequested).isEqualTo(120)
        }

        @Test
        fun `getFeed cached path should return null itemsDetailed when not stored in cache`() {
            val userId = UUID.randomUUID()
            val ids = (1..3).map { UUID.randomUUID() }
            val items = ids.map { it.toString() }.associateWith { createContentBatchItem(it.toString()) }

            every { feedCacheService.getCachedFeed(userId) } returns cachedFeed(ids, null)
            every { feedCacheService.tryAcquirePrefetchLock(userId) } returns false
            every { contentCacheService.getContent(any()) } returns null
            every { contentCacheService.isNegativeCached(any()) } returns false
            every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(items = items)
            every { contentCacheService.cacheContent(any(), any()) } just runs

            val result = feedService.getFeed(userId, null, false)

            assertThat(result.source).isEqualTo("cached")
            assertThat(result.itemsDetailed).isNull()
            assertThat(result.featureFlags).isNull()
        }
    }
}
