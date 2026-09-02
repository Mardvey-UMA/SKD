package com.contentplatform.user.integration.outbox

import com.contentplatform.user.db.repository.OutboxRepository
import com.contentplatform.user.infrastructure.outbox.OutboxPoller
import com.contentplatform.user.integration.IntegrationTestBase
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import java.time.Duration
import java.util.UUID

@Tag("integration")
class OutboxPollerIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var outboxPoller: OutboxPoller

    @Autowired
    lateinit var outboxRepository: OutboxRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var testConsumer: Consumer<String, String>

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM outbox")
        jdbcTemplate.execute("DELETE FROM profiles")

        val consumerProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "test-outbox-poller-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java
        )
        testConsumer = DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()
        testConsumer.subscribe(listOf("user.created"))
        // Wait for partition assignment before the test sends messages
        val deadline = System.currentTimeMillis() + 5000
        while (testConsumer.assignment().isEmpty() && System.currentTimeMillis() < deadline) {
            testConsumer.poll(Duration.ofMillis(100))
        }
    }

    @AfterEach
    fun tearDown() {
        testConsumer.close()
    }

    @Test
    fun `should publish unpublished outbox event to Kafka and mark as published`() {
        // Arrange - insert an unpublished outbox event directly via JDBC
        val userId = UUID.randomUUID()
        val email = "integration-test@example.com"
        val payload = """{"event_type":"user.created","user_id":"$userId","email":"$email","timestamp":"2026-04-05T10:00:00Z"}"""

        jdbcTemplate.update(
            "INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload, created_at) VALUES (?, ?, ?, ?, now())",
            "User",
            userId.toString(),
            "user.created",
            payload
        )

        // Act - trigger the poller
        outboxPoller.poll()

        // Assert - verify outbox event is marked as published
        val publishedCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox WHERE aggregate_id = ? AND published_at IS NOT NULL",
            Int::class.java,
            userId.toString()
        )
        assertThat(publishedCount).isEqualTo(1)

        // Assert - verify Kafka message was received on user.created topic
        val records = testConsumer.poll(Duration.ofSeconds(10))
        assertThat(records.count()).isGreaterThanOrEqualTo(1)

        val record = records.first()
        assertThat(record.topic()).isEqualTo("user.created")
        assertThat(record.key()).isEqualTo(userId.toString())
        assertThat(record.value()).contains(userId.toString())
        assertThat(record.value()).contains(email)
    }

    @Test
    fun `should not send anything when no unpublished outbox events exist`() {
        // Arrange - no outbox events inserted

        // Act
        outboxPoller.poll()

        // Assert - no messages on Kafka
        val records = testConsumer.poll(Duration.ofSeconds(3))
        assertThat(records.count()).isEqualTo(0)
    }

    @Test
    fun `should handle multiple unpublished events in a single poll`() {
        // Arrange - insert two unpublished outbox events
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        val payload1 = """{"event_type":"user.created","user_id":"$userId1","email":"user1@example.com","timestamp":"2026-04-05T10:00:00Z"}"""
        val payload2 = """{"event_type":"user.created","user_id":"$userId2","email":"user2@example.com","timestamp":"2026-04-05T10:01:00Z"}"""

        jdbcTemplate.update(
            "INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload, created_at) VALUES (?, ?, ?, ?, now())",
            "User", userId1.toString(), "user.created", payload1
        )
        jdbcTemplate.update(
            "INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload, created_at) VALUES (?, ?, ?, ?, now())",
            "User", userId2.toString(), "user.created", payload2
        )

        // Act
        outboxPoller.poll()

        // Assert - both events marked as published
        val publishedCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox WHERE published_at IS NOT NULL",
            Int::class.java
        )
        assertThat(publishedCount).isEqualTo(2)

        // Assert - both messages received on Kafka (poll until we have at least 2)
        val allRecords = mutableListOf<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>()
        val deadline = System.currentTimeMillis() + 10_000
        while (allRecords.size < 2 && System.currentTimeMillis() < deadline) {
            testConsumer.poll(Duration.ofMillis(500)).forEach { allRecords.add(it) }
        }
        assertThat(allRecords.size).isGreaterThanOrEqualTo(2)

        val keys = allRecords.map { it.key() }.toSet()
        assertThat(keys).contains(userId1.toString(), userId2.toString())
    }
}
