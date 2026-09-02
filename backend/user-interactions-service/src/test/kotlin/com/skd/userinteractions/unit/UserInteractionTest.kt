package com.skd.userinteractions.unit

import com.skd.userinteractions.domain.UserInteraction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class UserInteractionTest {

    @Test
    fun `entity contains all required fields`() {
        val userId = UUID.randomUUID()
        val contentId = UUID.randomUUID()
        val clientTs = Instant.parse("2026-04-03T12:00:00Z")
        val serverTs = Instant.parse("2026-04-03T12:00:01Z")

        val interaction = UserInteraction(
            id = null,
            userId = userId,
            contentId = contentId,
            actionType = "view",
            durationSec = 45,
            clientTs = clientTs,
            serverTs = serverTs
        )

        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(interaction.id).isNull()
            softly.assertThat(interaction.userId).isEqualTo(userId)
            softly.assertThat(interaction.contentId).isEqualTo(contentId)
            softly.assertThat(interaction.actionType).isEqualTo("view")
            softly.assertThat(interaction.durationSec).isEqualTo(45)
            softly.assertThat(interaction.clientTs).isEqualTo(clientTs)
            softly.assertThat(interaction.serverTs).isEqualTo(serverTs)
        }
    }

    @Test
    fun `duration_sec is nullable`() {
        val interaction = UserInteraction(
            id = null,
            userId = UUID.randomUUID(),
            contentId = UUID.randomUUID(),
            actionType = "click",
            durationSec = null,
            clientTs = Instant.now(),
            serverTs = Instant.now()
        )

        assertThat(interaction.durationSec).isNull()
    }

    @Test
    fun `id is nullable for new entities before persistence`() {
        val interaction = UserInteraction(
            id = null,
            userId = UUID.randomUUID(),
            contentId = UUID.randomUUID(),
            actionType = "share",
            durationSec = null,
            clientTs = Instant.now(),
            serverTs = Instant.now()
        )

        assertThat(interaction.id).isNull()
    }

    @Test
    fun `two interactions with same fields are equal`() {
        val userId = UUID.randomUUID()
        val contentId = UUID.randomUUID()
        val clientTs = Instant.parse("2026-04-03T12:00:00Z")
        val serverTs = Instant.parse("2026-04-03T12:00:01Z")

        val interaction1 = UserInteraction(
            id = 1L,
            userId = userId,
            contentId = contentId,
            actionType = "view",
            durationSec = 10,
            clientTs = clientTs,
            serverTs = serverTs
        )

        val interaction2 = UserInteraction(
            id = 1L,
            userId = userId,
            contentId = contentId,
            actionType = "view",
            durationSec = 10,
            clientTs = clientTs,
            serverTs = serverTs
        )

        assertThat(interaction1).isEqualTo(interaction2)
    }
}
