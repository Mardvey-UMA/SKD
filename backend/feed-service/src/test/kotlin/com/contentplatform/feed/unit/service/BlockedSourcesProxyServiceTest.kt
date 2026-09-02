package com.contentplatform.feed.unit.service

import com.contentplatform.feed.application.BlockedSourcesProxyService
import com.contentplatform.feed.db.repository.BlockedSourceRepository
import com.contentplatform.feed.db.repository.BlockedSourceWithMetadata
import com.contentplatform.feed.infrastructure.cache.FeedCacheService
import com.contentplatform.feed.infrastructure.client.RecSystemClient
import com.contentplatform.feed.infrastructure.client.RecSystemClientException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class BlockedSourcesProxyServiceTest {

    private val blockedSourceRepository = mockk<BlockedSourceRepository>(relaxed = true)
    private val recSystemClient = mockk<RecSystemClient>(relaxed = true)
    private val feedCacheService = mockk<FeedCacheService>(relaxed = true)
    private val service = BlockedSourcesProxyService(blockedSourceRepository, recSystemClient, feedCacheService)

    @Test
    fun `block persists locally`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()

        service.block(userId, sourceId)

        verify { blockedSourceRepository.insert(userId, sourceId) }
    }

    @Test
    fun `block invalidates feed cache`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()

        service.block(userId, sourceId)

        verify { feedCacheService.deleteFeed(userId.toString()) }
    }

    @Test
    fun `block swallows RecSystemClientException (rec-system is optional)`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        every { recSystemClient.blockSource(userId, sourceId) } throws RecSystemClientException("down")

        service.block(userId, sourceId)

        verify { blockedSourceRepository.insert(userId, sourceId) }
        verify { feedCacheService.deleteFeed(userId.toString()) }
    }

    @Test
    fun `unblock removes local row`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()

        service.unblock(userId, sourceId)

        verify { blockedSourceRepository.delete(userId, sourceId) }
    }

    @Test
    fun `unblock invalidates feed cache`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()

        service.unblock(userId, sourceId)

        verify { feedCacheService.deleteFeed(userId.toString()) }
    }

    @Test
    fun `unblock swallows RecSystemClientException`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        every { recSystemClient.unblockSource(userId, sourceId) } throws RecSystemClientException("down")

        service.unblock(userId, sourceId)

        verify { blockedSourceRepository.delete(userId, sourceId) }
    }

    @Test
    fun `list returns items enriched with source metadata and nulls for missing additions`() {
        val userId = UUID.randomUUID()
        val sourceWithMeta = UUID.randomUUID()
        val sourceWithoutMeta = UUID.randomUUID()
        val blockedAtWith = Instant.parse("2026-04-21T12:00:00Z")
        val blockedAtWithout = Instant.parse("2026-04-21T10:00:00Z")

        every { blockedSourceRepository.findByUserIdWithMetadata(userId) } returns listOf(
            BlockedSourceWithMetadata(
                userId = userId,
                sourceId = sourceWithMeta,
                blockedAt = blockedAtWith,
                sourceType = "rss",
                sourceName = "My Source"
            ),
            BlockedSourceWithMetadata(
                userId = userId,
                sourceId = sourceWithoutMeta,
                blockedAt = blockedAtWithout,
                sourceType = null,
                sourceName = null
            )
        )

        val response = service.list(userId)

        assertThat(response.items).hasSize(2)
        assertThat(response.count).isEqualTo(2)

        val first = response.items[0]
        assertThat(first.sourceId).isEqualTo(sourceWithMeta)
        assertThat(first.blockedAt).isEqualTo(blockedAtWith.toString())
        assertThat(first.sourceType).isEqualTo("rss")
        assertThat(first.sourceName).isEqualTo("My Source")

        val second = response.items[1]
        assertThat(second.sourceId).isEqualTo(sourceWithoutMeta)
        assertThat(second.blockedAt).isEqualTo(blockedAtWithout.toString())
        assertThat(second.sourceType).isNull()
        assertThat(second.sourceName).isNull()
    }

    @Test
    fun `list returns empty when nothing blocked`() {
        val userId = UUID.randomUUID()
        every { blockedSourceRepository.findByUserIdWithMetadata(userId) } returns emptyList()

        val response = service.list(userId)

        assertThat(response.items).isEmpty()
        assertThat(response.count).isEqualTo(0)
    }
}
