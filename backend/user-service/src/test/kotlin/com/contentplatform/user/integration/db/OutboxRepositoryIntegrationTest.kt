package com.contentplatform.user.integration.db

import com.contentplatform.user.db.repository.OutboxRepository
import com.contentplatform.user.db.repository.model.OutboxEventEntity
import com.contentplatform.user.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant

@Tag("integration")
class OutboxRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var outboxRepository: OutboxRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM outbox")
    }

    @Nested
    inner class `save` {

        @Test
        fun `should save outbox event and generate id`() {
            val event = OutboxEventEntity(
                aggregateType = "User",
                aggregateId = "user-123",
                eventType = "user.created",
                payload = """{"user_id":"user-123","email":"test@example.com"}"""
            )

            val saved = outboxRepository.save(event)

            assertThat(saved.id).isNotNull()
            assertThat(saved.aggregateType).isEqualTo("User")
            assertThat(saved.aggregateId).isEqualTo("user-123")
            assertThat(saved.eventType).isEqualTo("user.created")
            assertThat(saved.publishedAt).isNull()
        }
    }

    @Nested
    inner class `findUnpublished` {

        @Test
        fun `should return only unpublished events`() {
            outboxRepository.save(
                OutboxEventEntity(
                    aggregateType = "User",
                    aggregateId = "user-1",
                    eventType = "user.created",
                    payload = """{"user_id":"user-1"}"""
                )
            )

            // Insert a published event directly via JDBC
            jdbcTemplate.update(
                """
                INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload, published_at)
                VALUES ('User', 'user-2', 'user.created', '{"user_id":"user-2"}', now())
                """.trimIndent()
            )

            val unpublished = outboxRepository.findUnpublished(100)

            assertThat(unpublished).hasSize(1)
            assertThat(unpublished[0].aggregateId).isEqualTo("user-1")
        }

        @Test
        fun `should respect limit parameter`() {
            repeat(5) { i ->
                outboxRepository.save(
                    OutboxEventEntity(
                        aggregateType = "User",
                        aggregateId = "user-$i",
                        eventType = "user.created",
                        payload = """{"user_id":"user-$i"}"""
                    )
                )
            }

            val unpublished = outboxRepository.findUnpublished(3)

            assertThat(unpublished).hasSize(3)
        }

        @Test
        fun `should return empty list when no unpublished events`() {
            val unpublished = outboxRepository.findUnpublished(100)
            assertThat(unpublished).isEmpty()
        }

        @Test
        fun `should return events ordered by created_at`() {
            val event1 = outboxRepository.save(
                OutboxEventEntity(
                    aggregateType = "User",
                    aggregateId = "first",
                    eventType = "user.created",
                    payload = """{"user_id":"first"}"""
                )
            )
            val event2 = outboxRepository.save(
                OutboxEventEntity(
                    aggregateType = "User",
                    aggregateId = "second",
                    eventType = "user.created",
                    payload = """{"user_id":"second"}"""
                )
            )

            val unpublished = outboxRepository.findUnpublished(100)

            assertThat(unpublished).hasSize(2)
            assertThat(unpublished[0].id).isEqualTo(event1.id)
            assertThat(unpublished[1].id).isEqualTo(event2.id)
        }
    }

    @Nested
    inner class `markPublished` {

        @Test
        fun `should set published_at timestamp`() {
            val saved = outboxRepository.save(
                OutboxEventEntity(
                    aggregateType = "User",
                    aggregateId = "user-1",
                    eventType = "user.created",
                    payload = """{"user_id":"user-1"}"""
                )
            )

            val publishedAt = Instant.now()
            outboxRepository.markPublished(saved.id!!, publishedAt)

            val found = outboxRepository.findById(saved.id!!)
            assertThat(found).isPresent
            assertThat(found.get().publishedAt).isNotNull()
        }

        @Test
        fun `should exclude marked event from findUnpublished`() {
            val saved = outboxRepository.save(
                OutboxEventEntity(
                    aggregateType = "User",
                    aggregateId = "user-1",
                    eventType = "user.created",
                    payload = """{"user_id":"user-1"}"""
                )
            )

            outboxRepository.markPublished(saved.id!!, Instant.now())

            val unpublished = outboxRepository.findUnpublished(100)
            assertThat(unpublished).isEmpty()
        }
    }
}
