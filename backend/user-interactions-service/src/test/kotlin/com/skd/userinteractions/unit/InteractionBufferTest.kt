package com.skd.userinteractions.unit

import com.skd.userinteractions.api.model.interactions.InteractionEvent
import com.skd.userinteractions.application.InteractionBuffer
import com.skd.userinteractions.application.InteractionBatch
import com.skd.userinteractions.configuration.InteractionsProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

@Tag("unit")
class InteractionBufferTest {

    private val kafkaTemplate = mockk<KafkaTemplate<String, Any>>()
    private lateinit var properties: InteractionsProperties
    private lateinit var buffer: InteractionBuffer

    @BeforeEach
    fun setUp() {
        properties = InteractionsProperties().apply {
            batch = InteractionsProperties.Batch().apply {
                flushIntervalMs = 1000
                maxEventsPerUser = 5
            }
        }
        every { kafkaTemplate.send(any<String>(), any<String>(), any()) } returns CompletableFuture.completedFuture(mockk())
        buffer = InteractionBuffer(kafkaTemplate, properties)
    }

    private fun testEvent(
        contentId: String = UUID.randomUUID().toString(),
        actionType: String = "view",
        durationSec: Int? = null,
        timestamp: Instant = Instant.now().minusSeconds(10)
    ) = InteractionEvent(
        contentId = contentId,
        actionType = actionType,
        durationSec = durationSec,
        timestamp = timestamp
    )

    @Nested
    inner class `adding events` {

        @Test
        fun `addEvents buffers events for a userId`() {
            val userId = UUID.randomUUID()
            val events = listOf(testEvent(), testEvent())

            buffer.addEvents(userId, events)

            // Buffer should not flush yet (below threshold of 5)
            verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any()) }
        }

        @Test
        fun `addEvents accumulates events across multiple calls`() {
            val userId = UUID.randomUUID()

            buffer.addEvents(userId, listOf(testEvent()))
            buffer.addEvents(userId, listOf(testEvent()))

            // Still below threshold, no flush
            verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any()) }
        }
    }

    @Nested
    inner class `threshold flush` {

        @Test
        fun `flushes when events reach maxEventsPerUser threshold`() {
            val userId = UUID.randomUUID()
            val events = (1..5).map { testEvent() }

            buffer.addEvents(userId, events)

            // Should have flushed to Kafka
            verify(atLeast = 1) { kafkaTemplate.send(eq("user.interactions.batch"), eq(userId.toString()), any()) }
        }

        @Test
        fun `flush sends InteractionBatch with correct userId`() {
            val userId = UUID.randomUUID()
            val events = (1..5).map { testEvent() }

            val batchSlot = slot<InteractionBatch>()
            every { kafkaTemplate.send(any<String>(), any<String>(), capture(batchSlot)) } returns CompletableFuture.completedFuture(mockk())

            buffer.addEvents(userId, events)

            assertThat(batchSlot.captured.userId).isEqualTo(userId)
            assertThat(batchSlot.captured.interactions).hasSize(5)
            assertThat(batchSlot.captured.eventType).isEqualTo("user.interactions.batch")
            assertThat(batchSlot.captured.batchTs).isNotNull()
        }
    }

    @Nested
    inner class `scheduled flush` {

        @Test
        fun `flushAll sends buffered events for all users`() {
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()

            buffer.addEvents(userId1, listOf(testEvent(), testEvent()))
            buffer.addEvents(userId2, listOf(testEvent()))

            buffer.flushAll()

            verify(exactly = 1) { kafkaTemplate.send(eq("user.interactions.batch"), eq(userId1.toString()), any()) }
            verify(exactly = 1) { kafkaTemplate.send(eq("user.interactions.batch"), eq(userId2.toString()), any()) }
        }

        @Test
        fun `flushAll drains the buffer`() {
            val userId = UUID.randomUUID()
            buffer.addEvents(userId, listOf(testEvent()))

            buffer.flushAll()

            // Second flushAll should not send anything
            buffer.flushAll()

            verify(exactly = 1) { kafkaTemplate.send(eq("user.interactions.batch"), eq(userId.toString()), any()) }
        }

        @Test
        fun `flushAll does nothing when buffer is empty`() {
            buffer.flushAll()

            verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any()) }
        }
    }

    @Nested
    inner class `concurrent access` {

        @Test
        fun `handles concurrent addEvents for different users`() {
            val futures = (1..10).map { i ->
                CompletableFuture.runAsync {
                    val userId = UUID.randomUUID()
                    buffer.addEvents(userId, listOf(testEvent()))
                }
            }
            CompletableFuture.allOf(*futures.toTypedArray()).join()

            buffer.flushAll()

            // Should have sent messages for all 10 users
            verify(exactly = 10) { kafkaTemplate.send(eq("user.interactions.batch"), any<String>(), any()) }
        }
    }
}
