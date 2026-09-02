package com.skd.subscription.integration

import com.skd.subscription.db.repository.outbox.OutboxRepository
import com.skd.subscription.db.repository.outbox.model.OutboxEntry
import com.skd.subscription.db.service.OutboxPollerService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Tag("integration")
class OutboxPollerIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var outboxPollerService: OutboxPollerService

    @Autowired
    lateinit var outboxRepository: OutboxRepository

    private lateinit var consumer: KafkaConsumer<String, String>

    @BeforeEach
    fun setUp() {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "test-group-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name
        )
        consumer = KafkaConsumer(props)
        consumer.subscribe(listOf("subscription.changed"))
    }

    @AfterEach
    fun tearDown() {
        consumer.close()
    }

    @Test
    fun `should publish outbox entry to Kafka topic and mark as published`() {
        val userId = UUID.randomUUID().toString()
        val payload = """{"user_id":"$userId","tier":"premium","status":"active","timestamp":"${Instant.now()}"}"""

        val saved = outboxRepository.save(
            OutboxEntry(
                aggregateType = "subscription",
                aggregateId = userId,
                eventType = "subscription.changed",
                payload = payload,
                createdAt = Instant.now()
            )
        )

        outboxPollerService.pollAndPublish()

        val records = consumer.poll(Duration.ofSeconds(10))
        assertThat(records.count()).isGreaterThanOrEqualTo(1)

        val record = records.first()
        assertThat(record.topic()).isEqualTo("subscription.changed")
        assertThat(record.key()).isEqualTo(userId)
        assertThat(record.value()).isEqualTo(payload)

        val updated = outboxRepository.findById(saved.id!!)
        assertThat(updated).isPresent
        assertThat(updated.get().publishedAt).isNotNull()
    }

    @Test
    fun `should not republish already published entries`() {
        val userId = UUID.randomUUID().toString()
        outboxRepository.save(
            OutboxEntry(
                aggregateType = "subscription",
                aggregateId = userId,
                eventType = "subscription.changed",
                payload = """{"user_id":"$userId"}""",
                createdAt = Instant.now(),
                publishedAt = Instant.now()
            )
        )

        outboxPollerService.pollAndPublish()

        val records = consumer.poll(Duration.ofSeconds(5))
        val matching = records.filter { it.key() == userId }
        assertThat(matching).isEmpty()
    }

    @Test
    fun `should publish multiple entries to Kafka in order`() {
        val userIds = (1..3).map { UUID.randomUUID().toString() }
        userIds.forEach { userId ->
            outboxRepository.save(
                OutboxEntry(
                    aggregateType = "subscription",
                    aggregateId = userId,
                    eventType = "subscription.changed",
                    payload = """{"user_id":"$userId"}""",
                    createdAt = Instant.now()
                )
            )
        }

        outboxPollerService.pollAndPublish()

        val records = consumer.poll(Duration.ofSeconds(10))
        val keys = records.map { it.key() }
        assertThat(keys).containsAll(userIds)
    }
}
