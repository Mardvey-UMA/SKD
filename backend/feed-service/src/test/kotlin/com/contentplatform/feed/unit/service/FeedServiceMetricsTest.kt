package com.contentplatform.feed.unit.service

import com.contentplatform.feed.application.FeedService
import com.contentplatform.feed.db.repository.BlockedSourceRepository
import com.contentplatform.feed.db.repository.DislikeRepository
import com.contentplatform.feed.infrastructure.cache.CachedFeed
import com.contentplatform.feed.infrastructure.cache.ContentCacheService
import com.contentplatform.feed.infrastructure.cache.CursorCodec
import com.contentplatform.feed.infrastructure.cache.FeedCacheService
import com.contentplatform.feed.infrastructure.client.ContentAggregatorClient
import com.contentplatform.feed.infrastructure.client.RecSystemClient
import com.contentplatform.feed.infrastructure.client.model.ContentBatchResponse
import com.contentplatform.feed.infrastructure.client.model.ExtendedRecommendationsResponse
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class FeedServiceMetricsTest {

    private val feedCacheService = mockk<FeedCacheService>()
    private val contentCacheService = mockk<ContentCacheService>()
    private val recSystemClient = mockk<RecSystemClient>()
    private val contentAggregatorClient = mockk<ContentAggregatorClient>()
    private val dislikeRepository = mockk<DislikeRepository>(relaxed = true)
    private val blockedSourceRepository = mockk<BlockedSourceRepository>(relaxed = true)
    private val cursorCodec = CursorCodec()
    private val meterRegistry = SimpleMeterRegistry()

    private val feedService = FeedService(
        feedCacheService = feedCacheService,
        contentCacheService = contentCacheService,
        recSystemClient = recSystemClient,
        contentAggregatorClient = contentAggregatorClient,
        dislikeRepository = dislikeRepository,
        blockedSourceRepository = blockedSourceRepository,
        cursorCodec = cursorCodec,
        meterRegistry = meterRegistry,
        pageSize = 20,
        prefetchBatchSize = 120,
        prefetchTriggerPercent = 50,
        overfetchBuffer = 10,
        maxFetchIterations = 5
    )

    @Test
    fun `cache miss increments feed_request_count with source=personalized`() {
        val userId = UUID.randomUUID()
        val recIds = (1..25).map { UUID.randomUUID() }

        every { feedCacheService.getCachedFeed(userId) } returns null
        every { recSystemClient.getRecommendationsWithBreakdown(userId, 120) } returns
            ExtendedRecommendationsResponse(userId = userId, items = recIds, count = recIds.size)
        every { feedCacheService.put(userId, any(), any()) } just runs
        every { feedCacheService.tryAcquirePrefetchLock(userId) } returns false
        every { contentCacheService.getContent(any()) } returns null
        every { contentCacheService.isNegativeCached(any()) } returns false
        every { contentAggregatorClient.getContentBatch(any()) } returns ContentBatchResponse()

        feedService.getFeed(userId, null, false)

        val count = meterRegistry.find("feed_request_count").tag("source", "personalized").counter()?.count()
        assertThat(count).isEqualTo(1.0)
    }

    @Test
    fun `cache hit increments feed_request_count with source=cached`() {
        val userId = UUID.randomUUID()
        val cachedIds = (1..30).map { UUID.randomUUID() }

        every { feedCacheService.getCachedFeed(userId) } returns CachedFeed(
            items = cachedIds, itemsDetailed = null, cachedAt = Instant.now()
        )
        every { feedCacheService.tryAcquirePrefetchLock(userId) } returns false
        every { contentCacheService.getContent(any()) } returns null
        every { contentCacheService.isNegativeCached(any()) } returns false
        every { contentAggregatorClient.getContentBatch(any()) } returns ContentBatchResponse()

        feedService.getFeed(userId, null, false)

        val count = meterRegistry.find("feed_request_count").tag("source", "cached").counter()?.count()
        assertThat(count).isEqualTo(1.0)
    }
}
