package com.skd.userinteractions.unit

import com.skd.userinteractions.api.model.interactions.InteractionBatchRequest
import com.skd.userinteractions.api.model.interactions.InteractionBatchResponse
import com.skd.userinteractions.api.model.interactions.InteractionEvent
import com.skd.userinteractions.application.InteractionBuffer
import com.skd.userinteractions.domain.InteractionValidator
import com.skd.userinteractions.domain.UserInteraction
import com.skd.userinteractions.domain.ValidationResult
import com.skd.userinteractions.processor.InteractionProcessor
import com.skd.userinteractions.repository.UserInteractionRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class InteractionProcessorTest {

    private val validator = mockk<InteractionValidator>()
    private val repository = mockk<UserInteractionRepository>()
    private val buffer = mockk<InteractionBuffer>(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()
    private lateinit var processor: InteractionProcessor

    @BeforeEach
    fun setUp() {
        processor = InteractionProcessor(validator, repository, buffer, meterRegistry)
    }

    private fun testEvent(
        contentId: String = UUID.randomUUID().toString(),
        actionType: String = "view",
        durationSec: Int? = null,
        timestamp: Instant = Instant.now().minusSeconds(30)
    ) = InteractionEvent(
        contentId = contentId,
        actionType = actionType,
        durationSec = durationSec,
        timestamp = timestamp
    )

    @Nested
    inner class `processing batch` {

        @Test
        fun `returns response with accepted and rejected counts`() {
            val events = listOf(testEvent(), testEvent(), testEvent())
            val request = InteractionBatchRequest(events = events)
            val userId = UUID.randomUUID()

            every { validator.validate(request) } returns ValidationResult(
                accepted = listOf(events[0], events[1]),
                rejectedCount = 1
            )
            every { repository.saveAll(any<List<UserInteraction>>()) } returns emptyList()

            val response = processor.processBatch(userId, request)

            assertThat(response.accepted).isEqualTo(2)
            assertThat(response.rejected).isEqualTo(1)
        }

        @Test
        fun `saves accepted events to database`() {
            val event = testEvent()
            val request = InteractionBatchRequest(events = listOf(event))
            val userId = UUID.randomUUID()

            every { validator.validate(request) } returns ValidationResult(
                accepted = listOf(event),
                rejectedCount = 0
            )
            val savedSlot = slot<List<UserInteraction>>()
            every { repository.saveAll(capture(savedSlot)) } returns emptyList()

            processor.processBatch(userId, request)

            assertThat(savedSlot.captured).hasSize(1)
            assertThat(savedSlot.captured[0].userId).isEqualTo(userId)
            assertThat(savedSlot.captured[0].contentId).isEqualTo(UUID.fromString(event.contentId))
            assertThat(savedSlot.captured[0].actionType).isEqualTo("IMPRESSION") // "view" maps to canonical IMPRESSION
        }

        @Test
        fun `adds accepted events to buffer for Kafka batching`() {
            val event = testEvent()
            val request = InteractionBatchRequest(events = listOf(event))
            val userId = UUID.randomUUID()

            every { validator.validate(request) } returns ValidationResult(
                accepted = listOf(event),
                rejectedCount = 0
            )
            every { repository.saveAll(any<List<UserInteraction>>()) } returns emptyList()

            processor.processBatch(userId, request)

            verify(exactly = 1) { buffer.addEvents(userId, listOf(event.copy(actionType = "IMPRESSION"))) }
        }

        @Test
        fun `does not save rejected events to database`() {
            val request = InteractionBatchRequest(events = listOf(testEvent()))
            val userId = UUID.randomUUID()

            every { validator.validate(request) } returns ValidationResult(
                accepted = emptyList(),
                rejectedCount = 1
            )

            val response = processor.processBatch(userId, request)

            assertThat(response.accepted).isEqualTo(0)
            assertThat(response.rejected).isEqualTo(1)
            verify(exactly = 0) { repository.saveAll(any<List<UserInteraction>>()) }
        }

        @Test
        fun `propagates IllegalArgumentException from validator`() {
            val request = InteractionBatchRequest(events = emptyList())
            val userId = UUID.randomUUID()

            every { validator.validate(request) } throws IllegalArgumentException("Batch must not be empty")

            assertThatThrownBy { processor.processBatch(userId, request) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Batch must not be empty")
        }

        @Test
        fun `propagates scrollDepth from event to entity`() {
            val event = testEvent().copy(scrollDepth = 0.85)
            val request = InteractionBatchRequest(events = listOf(event))
            val userId = UUID.randomUUID()

            every { validator.validate(request) } returns ValidationResult(
                accepted = listOf(event),
                rejectedCount = 0
            )
            val savedSlot = slot<List<UserInteraction>>()
            every { repository.saveAll(capture(savedSlot)) } returns emptyList()

            processor.processBatch(userId, request)

            assertThat(savedSlot.captured[0].scrollDepth).isEqualTo(0.85f)
        }

        @Test
        fun `propagates metadata from event to entity`() {
            val event = testEvent().copy(metadata = mapOf("source" to "e2e"))
            val request = InteractionBatchRequest(events = listOf(event))
            val userId = UUID.randomUUID()

            every { validator.validate(request) } returns ValidationResult(
                accepted = listOf(event),
                rejectedCount = 0
            )
            val savedSlot = slot<List<UserInteraction>>()
            every { repository.saveAll(capture(savedSlot)) } returns emptyList()

            processor.processBatch(userId, request)

            assertThat(savedSlot.captured[0].metadata).isEqualTo(mapOf("source" to "e2e"))
        }

        @Test
        fun `null scrollDepth and null metadata are preserved for backward compat`() {
            val event = testEvent()
            val request = InteractionBatchRequest(events = listOf(event))
            val userId = UUID.randomUUID()

            every { validator.validate(request) } returns ValidationResult(
                accepted = listOf(event),
                rejectedCount = 0
            )
            val savedSlot = slot<List<UserInteraction>>()
            every { repository.saveAll(capture(savedSlot)) } returns emptyList()

            processor.processBatch(userId, request)

            assertThat(savedSlot.captured[0].scrollDepth).isNull()
            assertThat(savedSlot.captured[0].metadata).isNull()
        }

        @Test
        fun `increments interactions_batch_count counter with result accepted tag`() {
            val event = testEvent()
            val request = InteractionBatchRequest(events = listOf(event))
            val userId = UUID.randomUUID()

            every { validator.validate(request) } returns ValidationResult(
                accepted = listOf(event),
                rejectedCount = 0
            )
            every { repository.saveAll(any<List<UserInteraction>>()) } returns emptyList()

            processor.processBatch(userId, request)

            val counter = meterRegistry.find("interactions_batch_count")
                .tag("result", "accepted")
                .counter()
            assertThat(counter).isNotNull()
            assertThat(counter!!.count()).isEqualTo(1.0)
        }

        @Test
        fun `processes DISLIKE action`() {
            val event = testEvent(actionType = "dislike")
            val request = InteractionBatchRequest(events = listOf(event))
            val userId = UUID.randomUUID()

            every { validator.validate(request) } returns ValidationResult(
                accepted = listOf(event),
                rejectedCount = 0
            )
            val savedSlot = slot<List<UserInteraction>>()
            every { repository.saveAll(capture(savedSlot)) } returns emptyList()

            val response = processor.processBatch(userId, request)

            assertThat(savedSlot.captured).hasSize(1)
            assertThat(savedSlot.captured[0].actionType).isEqualTo("DISLIKE") // "dislike" is canonical DISLIKE
            assertThat(savedSlot.captured[0].userId).isEqualTo(userId)
            assertThat(savedSlot.captured[0].contentId).isEqualTo(UUID.fromString(event.contentId))
            verify(exactly = 1) { buffer.addEvents(userId, listOf(event.copy(actionType = "DISLIKE"))) }
            assertThat(response.accepted).isEqualTo(1)
            assertThat(response.rejected).isEqualTo(0)
        }
    }
}
