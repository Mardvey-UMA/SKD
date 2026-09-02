package com.skd.userinteractions.unit

import com.skd.userinteractions.api.model.interactions.InteractionBatchRequest
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class InteractionProcessorV2Test {

    private val validator = mockk<InteractionValidator>()
    private val repository = mockk<UserInteractionRepository>()
    private val buffer = mockk<InteractionBuffer>(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()
    private lateinit var processor: InteractionProcessor

    @BeforeEach
    fun setUp() {
        processor = InteractionProcessor(validator, repository, buffer, meterRegistry)
    }

    private fun savedEntities(): List<UserInteraction> {
        val slot = slot<List<UserInteraction>>()
        every { repository.saveAll(capture(slot)) } returns emptyList()
        return slot.captured
    }

    @Test
    fun `v1 event - new DB columns are null and ab_bucket defaults to 0`() {
        val event = InteractionEvent(
            contentId = UUID.randomUUID().toString(),
            actionType = "view",
            durationSec = null,
            timestamp = Instant.now().minusSeconds(30)
        )
        val request = InteractionBatchRequest(events = listOf(event))
        val userId = UUID.randomUUID()
        val capturedSlot = slot<List<UserInteraction>>()

        every { validator.validate(request) } returns ValidationResult(accepted = listOf(event), rejectedCount = 0)
        every { repository.saveAll(capture(capturedSlot)) } returns emptyList()

        processor.processBatch(userId, request)

        val entity = capturedSlot.captured[0]
        assertThat(entity.feedRequestId).isNull()
        assertThat(entity.positionInFeed).isNull()
        assertThat(entity.deviceType).isNull()
        assertThat(entity.appVersion).isNull()
        assertThat(entity.abBucket).isEqualTo(0.toShort())
    }

    @Test
    fun `v2 event - all new DB columns are populated from event DTO`() {
        val feedReqId = UUID.randomUUID()
        val event = InteractionEvent(
            contentId = UUID.randomUUID().toString(),
            actionType = "click",
            durationSec = 45,
            timestamp = Instant.now().minusSeconds(30),
            feedRequestId = feedReqId.toString(),
            positionInFeed = 3,
            deviceType = "ios",
            appVersion = "2.0.0",
            abBucket = 1
        )
        val request = InteractionBatchRequest(events = listOf(event))
        val userId = UUID.randomUUID()
        val capturedSlot = slot<List<UserInteraction>>()

        every { validator.validate(request) } returns ValidationResult(accepted = listOf(event), rejectedCount = 0)
        every { repository.saveAll(capture(capturedSlot)) } returns emptyList()

        processor.processBatch(userId, request)

        val entity = capturedSlot.captured[0]
        assertThat(entity.feedRequestId).isEqualTo(feedReqId)
        assertThat(entity.positionInFeed).isEqualTo(3.toShort())
        assertThat(entity.deviceType).isEqualTo("ios")
        assertThat(entity.appVersion).isEqualTo("2.0.0")
        assertThat(entity.abBucket).isEqualTo(1.toShort())
    }

    @Test
    fun `v2 event with ab_bucket 0 - ab_bucket stored as 0 not null`() {
        val event = InteractionEvent(
            contentId = UUID.randomUUID().toString(),
            actionType = "view",
            durationSec = null,
            timestamp = Instant.now().minusSeconds(30),
            abBucket = 0
        )
        val request = InteractionBatchRequest(events = listOf(event))
        val userId = UUID.randomUUID()
        val capturedSlot = slot<List<UserInteraction>>()

        every { validator.validate(request) } returns ValidationResult(accepted = listOf(event), rejectedCount = 0)
        every { repository.saveAll(capture(capturedSlot)) } returns emptyList()

        processor.processBatch(userId, request)

        assertThat(capturedSlot.captured[0].abBucket).isEqualTo(0.toShort())
    }

    @Test
    fun `v2 event with partial fields - only provided fields are populated`() {
        val event = InteractionEvent(
            contentId = UUID.randomUUID().toString(),
            actionType = "share",
            durationSec = null,
            timestamp = Instant.now().minusSeconds(30),
            deviceType = "web"
        )
        val request = InteractionBatchRequest(events = listOf(event))
        val userId = UUID.randomUUID()
        val capturedSlot = slot<List<UserInteraction>>()

        every { validator.validate(request) } returns ValidationResult(accepted = listOf(event), rejectedCount = 0)
        every { repository.saveAll(capture(capturedSlot)) } returns emptyList()

        processor.processBatch(userId, request)

        val entity = capturedSlot.captured[0]
        assertThat(entity.deviceType).isEqualTo("web")
        assertThat(entity.feedRequestId).isNull()
        assertThat(entity.positionInFeed).isNull()
        assertThat(entity.appVersion).isNull()
        assertThat(entity.abBucket).isEqualTo(0.toShort())
    }
}
