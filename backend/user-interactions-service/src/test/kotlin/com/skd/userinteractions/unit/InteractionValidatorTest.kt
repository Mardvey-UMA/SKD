package com.skd.userinteractions.unit

import com.skd.userinteractions.api.model.interactions.InteractionBatchRequest
import com.skd.userinteractions.api.model.interactions.InteractionEvent
import com.skd.userinteractions.domain.InteractionValidator
import com.skd.userinteractions.domain.ValidationResult
import com.skd.userinteractions.configuration.InteractionsProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class InteractionValidatorTest {

    private lateinit var validator: InteractionValidator
    private lateinit var properties: InteractionsProperties

    @BeforeEach
    fun setUp() {
        properties = InteractionsProperties().apply {
            validation = InteractionsProperties.Validation().apply {
                maxEventsPerBatch = 100
                maxTimestampFutureSec = 60
                maxTimestampAgeHours = 24
            }
        }
        validator = InteractionValidator(properties)
    }

    private fun validEvent(
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
    inner class `batch size validation` {

        @Test
        fun `valid batch with 1 event passes`() {
            val request = InteractionBatchRequest(events = listOf(validEvent()))
            val result = validator.validate(request)
            assertThat(result.accepted).hasSize(1)
            assertThat(result.rejectedCount).isEqualTo(0)
        }

        @Test
        fun `valid batch with 100 events passes`() {
            val events = (1..100).map { validEvent() }
            val request = InteractionBatchRequest(events = events)
            val result = validator.validate(request)
            assertThat(result.accepted).hasSize(100)
        }

        @Test
        fun `batch with 101 events is rejected entirely`() {
            val events = (1..101).map { validEvent() }
            val request = InteractionBatchRequest(events = events)

            assertThatThrownBy { validator.validate(request) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("100")
        }

        @Test
        fun `empty batch is rejected`() {
            val request = InteractionBatchRequest(events = emptyList())

            assertThatThrownBy { validator.validate(request) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class `timestamp validation` {

        @Test
        fun `event with future timestamp beyond tolerance is rejected`() {
            val futureEvent = validEvent(
                timestamp = Instant.now().plusSeconds(120)
            )
            val request = InteractionBatchRequest(events = listOf(futureEvent))
            val result = validator.validate(request)

            assertThat(result.accepted).isEmpty()
            assertThat(result.rejectedCount).isEqualTo(1)
        }

        @Test
        fun `event with timestamp older than 24h is rejected`() {
            val oldEvent = validEvent(
                timestamp = Instant.now().minusSeconds(25 * 3600)
            )
            val request = InteractionBatchRequest(events = listOf(oldEvent))
            val result = validator.validate(request)

            assertThat(result.accepted).isEmpty()
            assertThat(result.rejectedCount).isEqualTo(1)
        }

        @Test
        fun `event with timestamp within 60s in future is accepted`() {
            val nearFutureEvent = validEvent(
                timestamp = Instant.now().plusSeconds(30)
            )
            val request = InteractionBatchRequest(events = listOf(nearFutureEvent))
            val result = validator.validate(request)

            assertThat(result.accepted).hasSize(1)
            assertThat(result.rejectedCount).isEqualTo(0)
        }

        @Test
        fun `event with timestamp exactly at boundary of 24h ago is accepted`() {
            val boundaryEvent = validEvent(
                timestamp = Instant.now().minusSeconds(23 * 3600 + 3500)
            )
            val request = InteractionBatchRequest(events = listOf(boundaryEvent))
            val result = validator.validate(request)

            assertThat(result.accepted).hasSize(1)
        }
    }

    @Nested
    inner class `action type validation` {

        @Test
        fun `event with invalid action_type is rejected`() {
            val badEvent = validEvent(actionType = "garbage_action")
            val request = InteractionBatchRequest(events = listOf(badEvent))
            val result = validator.validate(request)

            assertThat(result.accepted).isEmpty()
            assertThat(result.rejectedCount).isEqualTo(1)
        }

        @Test
        fun `legacy action type names are accepted and map to canonical values`() {
            val events = listOf("view", "click", "scroll_past", "share", "save", "hide")
                .map { validEvent(actionType = it) }
            val request = InteractionBatchRequest(events = events)
            val result = validator.validate(request)

            assertThat(result.accepted).hasSize(6)
            assertThat(result.rejectedCount).isEqualTo(0)
        }

        @Test
        fun `canonical action type names are accepted`() {
            val events = listOf("IMPRESSION", "OPEN", "CLOSE", "LIKE", "DISLIKE", "BOOKMARK")
                .map { validEvent(actionType = it) }
            val request = InteractionBatchRequest(events = events)
            val result = validator.validate(request)

            assertThat(result.accepted).hasSize(6)
            assertThat(result.rejectedCount).isEqualTo(0)
        }
    }

    @Nested
    inner class `duration validation` {

        @Test
        fun `event with negative duration_sec is rejected`() {
            val badEvent = validEvent(durationSec = -1)
            val request = InteractionBatchRequest(events = listOf(badEvent))
            val result = validator.validate(request)

            assertThat(result.accepted).isEmpty()
            assertThat(result.rejectedCount).isEqualTo(1)
        }

        @Test
        fun `event with null duration_sec is accepted`() {
            val event = validEvent(durationSec = null)
            val request = InteractionBatchRequest(events = listOf(event))
            val result = validator.validate(request)

            assertThat(result.accepted).hasSize(1)
        }

        @Test
        fun `event with zero duration_sec is accepted`() {
            val event = validEvent(durationSec = 0)
            val request = InteractionBatchRequest(events = listOf(event))
            val result = validator.validate(request)

            assertThat(result.accepted).hasSize(1)
        }
    }

    @Nested
    inner class `content ID validation` {

        @Test
        fun `event with invalid UUID content_id is rejected`() {
            val badEvent = validEvent(contentId = "not-a-uuid")
            val request = InteractionBatchRequest(events = listOf(badEvent))
            val result = validator.validate(request)

            assertThat(result.accepted).isEmpty()
            assertThat(result.rejectedCount).isEqualTo(1)
        }

        @Test
        fun `event with empty content_id is rejected`() {
            val badEvent = validEvent(contentId = "")
            val request = InteractionBatchRequest(events = listOf(badEvent))
            val result = validator.validate(request)

            assertThat(result.accepted).isEmpty()
            assertThat(result.rejectedCount).isEqualTo(1)
        }
    }

    @Nested
    inner class `mixed valid and invalid events` {

        @Test
        fun `only valid events are accepted, invalid ones counted as rejected`() {
            val validEvent1 = validEvent(actionType = "view")
            val validEvent2 = validEvent(actionType = "click")
            val invalidFuture = validEvent(timestamp = Instant.now().plusSeconds(120))
            val invalidAction = validEvent(actionType = "invalid")
            val invalidDuration = validEvent(durationSec = -5)

            val request = InteractionBatchRequest(
                events = listOf(validEvent1, invalidFuture, validEvent2, invalidAction, invalidDuration)
            )
            val result = validator.validate(request)

            assertThat(result.accepted).hasSize(2)
            assertThat(result.rejectedCount).isEqualTo(3)
        }
    }
}
