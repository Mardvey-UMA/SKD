package com.contentplatform.auth.integration.service

import com.contentplatform.auth.db.repository.OutboxRepository
import com.contentplatform.auth.db.repository.model.OutboxEventEntity
import com.contentplatform.auth.integration.IntegrationTestBase
import com.contentplatform.auth.service.OutboxPollerService
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
import org.springframework.kafka.test.utils.KafkaTestUtils
import java.time.Duration
import java.util.UUID

@Tag("integration")
class OutboxPollerServiceIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var outboxPollerService: OutboxPollerService

    @Autowired
    lateinit var outboxRepository: OutboxRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var testConsumer: Consumer<String, String>

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM outbox")

        val consumerProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "test-outbox-poller-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java
        )
        testConsumer = DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()
        testConsumer.subscribe(listOf("user.registered"))
    }

    @AfterEach
    fun tearDown() {
        testConsumer.close()
    }

    @Test
    fun `should publish outbox entry to Kafka and mark as published in DB`() {
        val aggregateId = UUID.randomUUID().toString()
        val payload = """{"user_id":"$aggregateId","email":"test@example.com"}"""

        outboxRepository.save(
            OutboxEventEntity(
                aggregateType = "User",
                aggregateId = aggregateId,
                eventType = "user.registered",
                payload = payload
            )
        )

        outboxPollerService.pollAndPublish()

        // Verify message arrived in Kafka
        val records = KafkaTestUtils.getRecords(testConsumer, Duration.ofSeconds(10))
        val matchingRecords = records.records("user.registered")
            .filter { it.key() == aggregateId }
        assertThat(matchingRecords).isNotEmpty
        assertThat(matchingRecords.first().value()).isEqualTo(payload)

        // Verify published_at is set in DB
        val unpublished = outboxRepository.findUnpublished(100)
        assertThat(unpublished).isEmpty()
    }

    @Test
    fun `should publish multiple outbox entries in order`() {
        val id1 = UUID.randomUUID().toString()
        val id2 = UUID.randomUUID().toString()

        outboxRepository.save(
            OutboxEventEntity(
                aggregateType = "User",
                aggregateId = id1,
                eventType = "user.registered",
                payload = """{"user_id":"$id1"}"""
            )
        )
        outboxRepository.save(
            OutboxEventEntity(
                aggregateType = "User",
                aggregateId = id2,
                eventType = "user.registered",
                payload = """{"user_id":"$id2"}"""
            )
        )

        outboxPollerService.pollAndPublish()

        val records = KafkaTestUtils.getRecords(testConsumer, Duration.ofSeconds(10))
        val keys = records.records("user.registered").map { it.key() }
        assertThat(keys).contains(id1, id2)

        // Both should be marked published
        val unpublished = outboxRepository.findUnpublished(100)
        assertThat(unpublished).isEmpty()
    }

    @Test
    fun `should not publish anything when no unpublished entries exist`() {
        outboxPollerService.pollAndPublish()

        val records = KafkaTestUtils.getRecords(testConsumer, Duration.ofSeconds(3))
        assertThat(records.count()).isEqualTo(0)
    }
}
