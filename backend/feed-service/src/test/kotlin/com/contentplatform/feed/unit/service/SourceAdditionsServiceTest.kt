package com.contentplatform.feed.unit.service

import com.contentplatform.feed.application.SourceAdditionsService
import com.contentplatform.feed.db.repository.SourceAdditionsRepository
import com.contentplatform.feed.db.repository.model.SourceAdditionEntity
import com.contentplatform.feed.infrastructure.cache.CursorCodec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class SourceAdditionsServiceTest {

    private val repository = mockk<SourceAdditionsRepository>()
    private val cursorCodec = CursorCodec()

    private val service = SourceAdditionsService(
        repository = repository,
        cursorCodec = cursorCodec,
        defaultPageSize = 20,
        maxLimit = 50
    )

    @Test
    fun `recordAddition calls repository upsert`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val addedAt = Instant.parse("2026-04-16T10:00:00Z")
        every { repository.upsert(userId, sourceId, "rss", "Kotlin Weekly", addedAt) } returns 1

        service.recordAddition(userId, sourceId, "rss", "Kotlin Weekly", addedAt)

        verify { repository.upsert(userId, sourceId, "rss", "Kotlin Weekly", addedAt) }
    }

    @Test
    fun `listByUser paginates using plus one probe`() {
        val userId = UUID.randomUUID()
        val rows = (1..21).map {
            SourceAdditionEntity(
                userId = userId,
                sourceId = UUID.randomUUID(),
                sourceType = "rss",
                sourceName = "Src $it",
                addedAt = Instant.parse("2026-04-16T10:00:00Z")
            )
        }
        every { repository.findByUserId(userId, 21, 0) } returns rows

        val response = service.listByUser(userId, cursor = null, limit = 20)

        assertThat(response.items).hasSize(20)
        assertThat(response.hasNext).isTrue()
        assertThat(response.cursor).isNotNull()
    }

    @Test
    fun `listByUser returns hasNext false when last page`() {
        val userId = UUID.randomUUID()
        val rows = (1..5).map {
            SourceAdditionEntity(
                userId = userId,
                sourceId = UUID.randomUUID(),
                sourceType = "rss",
                sourceName = "Src $it",
                addedAt = Instant.parse("2026-04-16T10:00:00Z")
            )
        }
        every { repository.findByUserId(userId, 21, 0) } returns rows

        val response = service.listByUser(userId, cursor = null, limit = null)

        assertThat(response.items).hasSize(5)
        assertThat(response.hasNext).isFalse()
        assertThat(response.cursor).isNull()
    }

    @Test
    fun `listByUser rejects invalid limit`() {
        val userId = UUID.randomUUID()

        assertThatThrownBy {
            service.listByUser(userId, cursor = null, limit = 0)
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            service.listByUser(userId, cursor = null, limit = 100)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `countByUser delegates to repository`() {
        val userId = UUID.randomUUID()
        every { repository.countByUserId(userId) } returns 7

        val count = service.countByUser(userId)

        assertThat(count).isEqualTo(7)
    }
}
