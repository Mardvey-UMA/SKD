package com.contentplatform.feed.unit.service

import com.contentplatform.feed.api.exception.DuplicateSpaceNameException
import com.contentplatform.feed.api.exception.NotFoundException
import com.contentplatform.feed.api.exception.SpaceLimitReachedException
import com.contentplatform.feed.application.SpaceService
import com.contentplatform.feed.application.dto.CreateSpaceRequest
import com.contentplatform.feed.application.dto.UpdateSpaceRequest
import com.contentplatform.feed.db.repository.SpaceRepository
import com.contentplatform.feed.db.repository.SpaceSourceRepository
import com.contentplatform.feed.db.repository.model.SpaceEntity
import com.contentplatform.feed.domain.SpaceColor
import com.contentplatform.feed.infrastructure.cache.FeedCacheService
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
class SpaceServiceTest {

    private val spaceRepository = mockk<SpaceRepository>()
    private val spaceSourceRepository = mockk<SpaceSourceRepository>(relaxed = true)
    private val feedCacheService = mockk<FeedCacheService>(relaxed = true)

    private val service = SpaceService(
        spaceRepository = spaceRepository,
        spaceSourceRepository = spaceSourceRepository,
        feedCacheService = feedCacheService,
        maxSpacesPerUser = 10
    )

    private fun sampleEntity(
        id: UUID = UUID.randomUUID(),
        userId: UUID = UUID.randomUUID(),
        name: String = "My Space",
        color: SpaceColor = SpaceColor.BLUE
    ): SpaceEntity {
        val now = Instant.now()
        return SpaceEntity(id, userId, name, color, now, now)
    }

    @Test
    fun `create rejects 11th space`() {
        val userId = UUID.randomUUID()
        every { spaceRepository.countByUserIdForUpdate(userId) } returns 10

        assertThatThrownBy {
            service.create(userId, CreateSpaceRequest(name = "X", color = "BLUE", sourceIds = emptyList()))
        }.isInstanceOf(SpaceLimitReachedException::class.java)
    }

    @Test
    fun `create rejects duplicate name`() {
        val userId = UUID.randomUUID()
        every { spaceRepository.countByUserIdForUpdate(userId) } returns 3
        every { spaceRepository.existsByUserIdAndName(userId, "Existing") } returns true

        assertThatThrownBy {
            service.create(userId, CreateSpaceRequest(name = "Existing", color = "BLUE", sourceIds = null))
        }.isInstanceOf(DuplicateSpaceNameException::class.java)
    }

    @Test
    fun `create with empty source ids succeeds and does not call insertAll with data`() {
        val userId = UUID.randomUUID()
        val entity = sampleEntity(userId = userId, name = "New", color = SpaceColor.RED)
        every { spaceRepository.countByUserIdForUpdate(userId) } returns 0
        every { spaceRepository.existsByUserIdAndName(userId, "New") } returns false
        every { spaceRepository.insert(userId, "New", SpaceColor.RED) } returns entity

        val response = service.create(userId, CreateSpaceRequest(name = "New", color = "RED", sourceIds = emptyList()))

        assertThat(response.sourceIds).isEmpty()
        assertThat(response.color).isEqualTo("RED")
    }

    @Test
    fun `create dedupes duplicate source uuids`() {
        val userId = UUID.randomUUID()
        val entity = sampleEntity(userId = userId)
        val src = UUID.randomUUID()
        every { spaceRepository.countByUserIdForUpdate(userId) } returns 0
        every { spaceRepository.existsByUserIdAndName(userId, any()) } returns false
        every { spaceRepository.insert(userId, any(), any()) } returns entity

        val response = service.create(userId, CreateSpaceRequest(name = "K", color = "BLUE", sourceIds = listOf(src, src, src)))

        verify { spaceSourceRepository.insertAll(entity.id, listOf(src)) }
        assertThat(response.sourceIds).containsExactly(src)
    }

    @Test
    fun `update with only name does NOT invalidate cache`() {
        val userId = UUID.randomUUID()
        val spaceId = UUID.randomUUID()
        val existing = sampleEntity(id = spaceId, userId = userId, name = "Old", color = SpaceColor.BLUE)
        every { spaceRepository.findByIdAndUserId(spaceId, userId) } returnsMany listOf(existing, existing.copy(name = "New"))
        every { spaceRepository.existsByUserIdAndNameExcludingId(userId, "New", spaceId) } returns false
        every { spaceRepository.update(spaceId, "New", null) } returns 1
        every { spaceSourceRepository.findSourceIds(spaceId) } returns emptyList()

        service.update(userId, spaceId, UpdateSpaceRequest(name = "New"))

        verify(exactly = 0) { feedCacheService.deleteFeed(any<String>()) }
    }

    @Test
    fun `update with source_ids invalidates cache`() {
        val userId = UUID.randomUUID()
        val spaceId = UUID.randomUUID()
        val existing = sampleEntity(id = spaceId, userId = userId)
        val src1 = UUID.randomUUID()
        every { spaceRepository.findByIdAndUserId(spaceId, userId) } returns existing
        every { spaceRepository.touchUpdatedAt(spaceId) } returns 1

        service.update(userId, spaceId, UpdateSpaceRequest(sourceIds = listOf(src1)))

        verify { feedCacheService.deleteFeed("feed:space:$spaceId:user:$userId") }
        verify { spaceSourceRepository.replaceAll(spaceId, listOf(src1)) }
    }

    @Test
    fun `delete throws NotFound when space missing`() {
        val userId = UUID.randomUUID()
        val spaceId = UUID.randomUUID()
        every { spaceRepository.deleteByIdAndUserId(spaceId, userId) } returns 0

        assertThatThrownBy {
            service.delete(userId, spaceId)
        }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `delete invalidates cache`() {
        val userId = UUID.randomUUID()
        val spaceId = UUID.randomUUID()
        every { spaceRepository.deleteByIdAndUserId(spaceId, userId) } returns 1
        every { feedCacheService.deleteFeed(any<String>()) } just runs

        service.delete(userId, spaceId)

        verify { feedCacheService.deleteFeed("feed:space:$spaceId:user:$userId") }
    }

    @Test
    fun `getById throws NotFound when wrong owner`() {
        val userId = UUID.randomUUID()
        val spaceId = UUID.randomUUID()
        every { spaceRepository.findByIdAndUserId(spaceId, userId) } returns null

        assertThatThrownBy {
            service.getById(userId, spaceId)
        }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `create rejects invalid color`() {
        val userId = UUID.randomUUID()
        every { spaceRepository.countByUserIdForUpdate(userId) } returns 0

        assertThatThrownBy {
            service.create(userId, CreateSpaceRequest(name = "X", color = "MAGENTA", sourceIds = null))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `create rejects blank name`() {
        val userId = UUID.randomUUID()
        every { spaceRepository.countByUserIdForUpdate(userId) } returns 0

        assertThatThrownBy {
            service.create(userId, CreateSpaceRequest(name = "   ", color = "BLUE", sourceIds = null))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
