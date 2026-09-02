package com.contentplatform.feed.unit.service

import com.contentplatform.feed.api.exception.NotFoundException
import com.contentplatform.feed.api.exception.SpaceFeedUnavailableException
import com.contentplatform.feed.application.SpaceFeedService
import com.contentplatform.feed.db.repository.DislikeRepository
import com.contentplatform.feed.db.repository.SpaceRepository
import com.contentplatform.feed.db.repository.SpaceSourceRepository
import com.contentplatform.feed.db.repository.model.SpaceEntity
import com.contentplatform.feed.domain.SpaceColor
import com.contentplatform.feed.infrastructure.cache.ContentCacheService
import com.contentplatform.feed.infrastructure.cache.CursorCodec
import com.contentplatform.feed.infrastructure.cache.FeedCacheService
import com.contentplatform.feed.infrastructure.client.ContentAggregatorClient
import com.contentplatform.feed.infrastructure.client.RecSystemClient
import com.contentplatform.feed.infrastructure.client.RecSystemClientException
import com.contentplatform.feed.infrastructure.client.model.ContentBatchItem
import com.contentplatform.feed.infrastructure.client.model.ContentBatchResponse
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class SpaceFeedServiceTest {

    private val spaceRepository = mockk<SpaceRepository>()
    private val spaceSourceRepository = mockk<SpaceSourceRepository>()
    private val feedCacheService = mockk<FeedCacheService>(relaxed = true)
    private val contentCacheService = mockk<ContentCacheService>(relaxed = true)
    private val recSystemClient = mockk<RecSystemClient>()
    private val contentAggregatorClient = mockk<ContentAggregatorClient>()
    private val dislikeRepository = mockk<DislikeRepository>(relaxed = true)
    private val cursorCodec = CursorCodec()

    private val service = SpaceFeedService(
        spaceRepository = spaceRepository,
        spaceSourceRepository = spaceSourceRepository,
        feedCacheService = feedCacheService,
        contentCacheService = contentCacheService,
        recSystemClient = recSystemClient,
        contentAggregatorClient = contentAggregatorClient,
        dislikeRepository = dislikeRepository,
        cursorCodec = cursorCodec,
        defaultPageSize = 20,
        prefetchBatchSize = 120,
        prefetchTriggerPercent = 50,
        overfetchBuffer = 10,
        maxFetchIterations = 5,
        maxLimit = 50
    )

    private fun sampleSpace(spaceId: UUID, userId: UUID) = SpaceEntity(
        id = spaceId,
        userId = userId,
        name = "Space",
        color = SpaceColor.BLUE,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun `throws NotFound when space missing`() {
        val userId = UUID.randomUUID()
        val spaceId = UUID.randomUUID()
        every { spaceRepository.findByIdAndUserId(spaceId, userId) } returns null

        assertThatThrownBy { service.getSpaceFeed(userId, spaceId, null, null) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `returns empty response when space has no sources`() {
        val userId = UUID.randomUUID()
        val spaceId = UUID.randomUUID()
        every { spaceRepository.findByIdAndUserId(spaceId, userId) } returns sampleSpace(spaceId, userId)
        every { spaceSourceRepository.findSourceIds(spaceId) } returns emptyList()

        val response = service.getSpaceFeed(userId, spaceId, null, null)

        assertThat(response.items).isEmpty()
        assertThat(response.hasNext).isFalse()
        assertThat(response.cursor).isNull()
        verify(exactly = 0) { recSystemClient.getRecommendationsNarrow(any(), any(), any()) }
    }

    @Test
    fun `cache miss triggers NARROW call and caches result`() {
        val userId = UUID.randomUUID()
        val spaceId = UUID.randomUUID()
        val sourceIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        val recIds = (1..40).map { UUID.randomUUID() }
        val stringIds = recIds.map { it.toString() }
        val expectedKey = "feed:space:$spaceId:user:$userId"

        every { spaceRepository.findByIdAndUserId(spaceId, userId) } returns sampleSpace(spaceId, userId)
        every { spaceSourceRepository.findSourceIds(spaceId) } returns sourceIds
        every { feedCacheService.getFeedSize(expectedKey) } returns 0L
        every { recSystemClient.getRecommendationsNarrow(userId, 120, sourceIds) } returns recIds
        every { feedCacheService.cacheFeed(expectedKey, stringIds) } just runs
        every { feedCacheService.getFeedPage(expectedKey, 0L, 30L) } returns stringIds.take(30)
        every { contentCacheService.getContent(any()) } returns null
        every { contentCacheService.isNegativeCached(any()) } returns false
        every { contentAggregatorClient.getContentBatch(any(), any(), any()) } returns ContentBatchResponse(
            items = stringIds.take(20).associateWith { ContentBatchItem(id = it) }
        )

        val response = service.getSpaceFeed(userId, spaceId, null, null)

        assertThat(response.items).hasSize(20)
        verify { recSystemClient.getRecommendationsNarrow(userId, 120, sourceIds) }
        verify { feedCacheService.cacheFeed(expectedKey, stringIds) }
    }

    @Test
    fun `rec-system failure maps to SpaceFeedUnavailableException`() {
        val userId = UUID.randomUUID()
        val spaceId = UUID.randomUUID()
        val sourceIds = listOf(UUID.randomUUID())
        val expectedKey = "feed:space:$spaceId:user:$userId"

        every { spaceRepository.findByIdAndUserId(spaceId, userId) } returns sampleSpace(spaceId, userId)
        every { spaceSourceRepository.findSourceIds(spaceId) } returns sourceIds
        every { feedCacheService.getFeedSize(expectedKey) } returns 0L
        every { recSystemClient.getRecommendationsNarrow(userId, 120, sourceIds) } throws RecSystemClientException("down")

        assertThatThrownBy { service.getSpaceFeed(userId, spaceId, null, null) }
            .isInstanceOf(SpaceFeedUnavailableException::class.java)
    }

    @Test
    fun `invalid limit throws IllegalArgumentException`() {
        val userId = UUID.randomUUID()
        val spaceId = UUID.randomUUID()

        assertThatThrownBy {
            service.getSpaceFeed(userId, spaceId, null, 0)
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            service.getSpaceFeed(userId, spaceId, null, 100)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
