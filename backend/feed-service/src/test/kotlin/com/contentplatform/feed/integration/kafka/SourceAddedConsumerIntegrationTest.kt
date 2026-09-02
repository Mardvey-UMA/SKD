package com.contentplatform.feed.integration.kafka

import com.contentplatform.feed.db.repository.SourceAdditionsRepository
import com.contentplatform.feed.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.util.UUID

@Tag("integration")
class SourceAddedConsumerIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, String>
    @Autowired lateinit var repository: SourceAdditionsRepository
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM feed.source_additions")
    }

    @Test
    fun `consumer records new source addition`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()

        val payload = """
            {
                "event_id": "${UUID.randomUUID()}",
                "event_type": "source.added",
                "source_id": "$sourceId",
                "user_id": "$userId",
                "source_type": "rss",
                "name": "Kotlin Weekly",
                "added_at": "2026-04-16T10:00:00Z"
            }
        """.trimIndent()

        kafkaTemplate.send("source.added", userId.toString(), payload)

        await atMost Duration.ofSeconds(20) untilAsserted {
            assertThat(repository.countByUserId(userId)).isEqualTo(1)
        }
        val rows = repository.findByUserId(userId, 10, 0)
        assertThat(rows).hasSize(1)
        assertThat(rows.first().sourceId).isEqualTo(sourceId)
        assertThat(rows.first().sourceType).isEqualTo("rss")
        assertThat(rows.first().sourceName).isEqualTo("Kotlin Weekly")
    }

    @Test
    fun `duplicate event is idempotent`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()

        val payload = """
            {
                "event_id": "${UUID.randomUUID()}",
                "event_type": "source.added",
                "source_id": "$sourceId",
                "user_id": "$userId",
                "source_type": "rss",
                "name": "Duplicate Test",
                "added_at": "2026-04-16T10:00:00Z"
            }
        """.trimIndent()

        kafkaTemplate.send("source.added", userId.toString(), payload)
        kafkaTemplate.send("source.added", userId.toString(), payload)
        kafkaTemplate.send("source.added", userId.toString(), payload)

        await atMost Duration.ofSeconds(20) untilAsserted {
            assertThat(repository.countByUserId(userId)).isEqualTo(1)
        }
    }
}
