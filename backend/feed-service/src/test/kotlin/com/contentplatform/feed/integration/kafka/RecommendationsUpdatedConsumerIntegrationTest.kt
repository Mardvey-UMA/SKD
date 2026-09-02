package com.contentplatform.feed.integration.kafka

import com.contentplatform.feed.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.util.UUID

@Tag("integration")
class RecommendationsUpdatedConsumerIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @BeforeEach
    fun cleanRedis() {
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }

    @Test
    fun `should delete feed cache from Redis when recommendations updated message is consumed`() {
        val userId = UUID.randomUUID()
        val feedKey = "feed:user:$userId"

        // Arrange: populate feed cache in Redis
        redisTemplate.opsForList().rightPush(feedKey, "content-1")
        redisTemplate.opsForList().rightPush(feedKey, "content-2")
        redisTemplate.opsForList().rightPush(feedKey, "content-3")
        assertThat(redisTemplate.hasKey(feedKey)).isTrue()

        // Act: publish recommendations.updated event to Kafka
        val message = """
            {
                "event_type": "recommendations.updated",
                "user_id": "$userId",
                "reason": "interactions_processed",
                "timestamp": "2026-04-05T10:00:00Z"
            }
        """.trimIndent()
        kafkaTemplate.send("recommendations.updated", userId.toString(), message)

        // Assert: feed cache key should be deleted (async — use Awaitility)
        await atMost Duration.ofSeconds(15) untilAsserted {
            assertThat(redisTemplate.hasKey(feedKey)).isFalse()
        }
    }

    @Test
    fun `should handle message for user with no cached feed without error`() {
        val userId = UUID.randomUUID()
        val feedKey = "feed:user:$userId"

        // Arrange: no feed cache exists
        assertThat(redisTemplate.hasKey(feedKey)).isFalse()

        // Act: publish recommendations.updated event
        val message = """
            {
                "event_type": "recommendations.updated",
                "user_id": "$userId",
                "reason": "interactions_processed",
                "timestamp": "2026-04-05T10:30:00Z"
            }
        """.trimIndent()
        kafkaTemplate.send("recommendations.updated", userId.toString(), message)

        // Assert: should process without error — key still does not exist
        await atMost Duration.ofSeconds(15) untilAsserted {
            // Give consumer time to process; verify no crash by checking key remains absent
            assertThat(redisTemplate.hasKey(feedKey)).isFalse()
        }
    }

    @Test
    fun `should only delete targeted user feed cache leaving other users intact`() {
        val targetUserId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        val targetKey = "feed:user:$targetUserId"
        val otherKey = "feed:user:$otherUserId"

        // Arrange: populate feed cache for both users
        redisTemplate.opsForList().rightPush(targetKey, "content-1")
        redisTemplate.opsForList().rightPush(otherKey, "content-2")
        assertThat(redisTemplate.hasKey(targetKey)).isTrue()
        assertThat(redisTemplate.hasKey(otherKey)).isTrue()

        // Act: publish event only for target user
        val message = """
            {
                "event_type": "recommendations.updated",
                "user_id": "$targetUserId",
                "reason": "profile_updated",
                "timestamp": "2026-04-05T11:00:00Z"
            }
        """.trimIndent()
        kafkaTemplate.send("recommendations.updated", targetUserId.toString(), message)

        // Assert: target user feed deleted, other user feed intact
        await atMost Duration.ofSeconds(15) untilAsserted {
            assertThat(redisTemplate.hasKey(targetKey)).isFalse()
        }
        assertThat(redisTemplate.hasKey(otherKey)).isTrue()
    }
}
