package com.skd.userinteractions.unit

import com.skd.userinteractions.api.model.interactions.InteractionEvent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class InteractionEventTest {

    @Test
    fun `valid event with all fields`() {
        val contentId = UUID.randomUUID().toString()
        val timestamp = Instant.now().minusSeconds(60)

        val event = InteractionEvent(
            contentId = contentId,
            actionType = "view",
            durationSec = 45,
            timestamp = timestamp
        )

        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(event.contentId).isEqualTo(contentId)
            softly.assertThat(event.actionType).isEqualTo("view")
            softly.assertThat(event.durationSec).isEqualTo(45)
            softly.assertThat(event.timestamp).isEqualTo(timestamp)
        }
    }

    @Test
    fun `valid event without durationSec`() {
        val event = InteractionEvent(
            contentId = UUID.randomUUID().toString(),
            actionType = "click",
            durationSec = null,
            timestamp = Instant.now().minusSeconds(10)
        )

        assertThat(event.durationSec).isNull()
    }

    @Test
    fun `contentId stores UUID string`() {
        val uuid = UUID.randomUUID()
        val event = InteractionEvent(
            contentId = uuid.toString(),
            actionType = "share",
            durationSec = null,
            timestamp = Instant.now().minusSeconds(5)
        )

        assertThat(event.contentId).isEqualTo(uuid.toString())
        // Verify it is a valid UUID format
        assertThat(UUID.fromString(event.contentId)).isNotNull
    }
}
