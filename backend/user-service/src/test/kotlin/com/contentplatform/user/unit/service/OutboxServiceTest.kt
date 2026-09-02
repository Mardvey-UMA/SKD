package com.contentplatform.user.unit.service

import com.contentplatform.user.application.service.OutboxService
import com.contentplatform.user.db.repository.OutboxRepository
import com.contentplatform.user.db.repository.model.OutboxEventEntity
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class OutboxServiceTest {

    private val outboxRepository = mockk<OutboxRepository>()
    private val outboxService = OutboxService(outboxRepository)

    @Nested
    inner class `createUserCreatedEvent` {

        @Test
        fun `should save outbox entry with correct event type`() {
            val entitySlot = slot<OutboxEventEntity>()
            every { outboxRepository.save(capture(entitySlot)) } answers { entitySlot.captured.copy(id = 1L) }

            val userId = UUID.randomUUID()
            outboxService.createUserCreatedEvent(userId, "user@example.com")

            assertThat(entitySlot.captured.eventType).isEqualTo("user.created")
        }

        @Test
        fun `should save outbox entry with correct aggregate type`() {
            val entitySlot = slot<OutboxEventEntity>()
            every { outboxRepository.save(capture(entitySlot)) } answers { entitySlot.captured.copy(id = 1L) }

            val userId = UUID.randomUUID()
            outboxService.createUserCreatedEvent(userId, "user@example.com")

            assertThat(entitySlot.captured.aggregateType).isEqualTo("User")
        }

        @Test
        fun `should save outbox entry with user id as aggregate id`() {
            val entitySlot = slot<OutboxEventEntity>()
            every { outboxRepository.save(capture(entitySlot)) } answers { entitySlot.captured.copy(id = 1L) }

            val userId = UUID.randomUUID()
            outboxService.createUserCreatedEvent(userId, "user@example.com")

            assertThat(entitySlot.captured.aggregateId).isEqualTo(userId.toString())
        }

        @Test
        fun `should save outbox entry with payload containing user id and email`() {
            val entitySlot = slot<OutboxEventEntity>()
            every { outboxRepository.save(capture(entitySlot)) } answers { entitySlot.captured.copy(id = 1L) }

            val userId = UUID.randomUUID()
            val email = "payload@example.com"
            outboxService.createUserCreatedEvent(userId, email)

            val payload = entitySlot.captured.payload
            assertThat(payload).contains(userId.toString())
            assertThat(payload).contains(email)
        }

        @Test
        fun `should save outbox entry with payload containing event type`() {
            val entitySlot = slot<OutboxEventEntity>()
            every { outboxRepository.save(capture(entitySlot)) } answers { entitySlot.captured.copy(id = 1L) }

            val userId = UUID.randomUUID()
            outboxService.createUserCreatedEvent(userId, "user@example.com")

            assertThat(entitySlot.captured.payload).contains("user.created")
        }

        @Test
        fun `should save outbox entry with null published at`() {
            val entitySlot = slot<OutboxEventEntity>()
            every { outboxRepository.save(capture(entitySlot)) } answers { entitySlot.captured.copy(id = 1L) }

            outboxService.createUserCreatedEvent(UUID.randomUUID(), "user@example.com")

            assertThat(entitySlot.captured.publishedAt).isNull()
        }
    }

    @Nested
    inner class `findUnpublished` {

        @Test
        fun `should delegate to repository with given limit`() {
            val expectedEntries = listOf(
                OutboxEventEntity(
                    id = 1L,
                    aggregateType = "User",
                    aggregateId = UUID.randomUUID().toString(),
                    eventType = "user.created",
                    payload = "{}",
                    createdAt = Instant.now()
                )
            )
            every { outboxRepository.findUnpublished(10) } returns expectedEntries

            val result = outboxService.findUnpublished(10)

            assertThat(result).isEqualTo(expectedEntries)
            verify(exactly = 1) { outboxRepository.findUnpublished(10) }
        }

        @Test
        fun `should return empty list when no unpublished entries`() {
            every { outboxRepository.findUnpublished(any()) } returns emptyList()

            val result = outboxService.findUnpublished(50)

            assertThat(result).isEmpty()
        }
    }

    @Nested
    inner class `markPublished` {

        @Test
        fun `should delegate to repository with id and current timestamp`() {
            val idSlot = slot<Long>()
            val timestampSlot = slot<Instant>()
            every { outboxRepository.markPublished(capture(idSlot), capture(timestampSlot)) } returns Unit

            val before = Instant.now()
            outboxService.markPublished(42L)
            val after = Instant.now()

            assertThat(idSlot.captured).isEqualTo(42L)
            assertThat(timestampSlot.captured).isBetween(before, after)
        }

        @Test
        fun `should call repository exactly once`() {
            every { outboxRepository.markPublished(any(), any()) } returns Unit

            outboxService.markPublished(99L)

            verify(exactly = 1) { outboxRepository.markPublished(99L, any()) }
        }
    }
}
