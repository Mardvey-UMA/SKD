package com.skd.userinteractions.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.skd.userinteractions.api.model.interactions.InteractionEvent
import com.skd.userinteractions.application.InteractionBatch
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Tag("integration")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class KafkaProducerIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("interactions_db")
            .withUsername("test")
            .withPassword("test")

        @Container
        @JvmStatic
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
        }
    }

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private lateinit var consumer: org.apache.kafka.clients.consumer.Consumer<String, String>

    @BeforeEach
    fun setUpConsumer() {
        val consumerProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "test-group-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java
        )
        consumer = DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()
        consumer.subscribe(listOf("user.interactions.batch"))
        // Wait until partition assignment is complete before the test sends messages
        val deadline = System.currentTimeMillis() + 5000L
        while (consumer.assignment().isEmpty() && System.currentTimeMillis() < deadline) {
            consumer.poll(java.time.Duration.ofMillis(100))
        }
    }

    @AfterEach
    fun tearDownConsumer() {
        consumer.close()
    }

    @Test
    fun `sends InteractionBatch to user_interactions_batch topic`() {
        val userId = UUID.randomUUID()
        val batch = InteractionBatch(
            eventType = "user.interactions.batch",
            userId = userId,
            interactions = listOf(
                InteractionEvent(
                    contentId = UUID.randomUUID().toString(),
                    actionType = "view",
                    durationSec = 15,
                    timestamp = Instant.now().minusSeconds(10)
                )
            ),
            batchTs = Instant.now()
        )

        kafkaTemplate.send("user.interactions.batch", userId.toString(), batch).get()

        val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10))
        assertThat(records.count()).isGreaterThan(0)
        assertThat(records.map { it.key() }).contains(userId.toString())
        assertThat(records.first { it.key() == userId.toString() }.topic()).isEqualTo("user.interactions.batch")
    }

    @Test
    fun `message key is userId as string`() {
        val userId = UUID.randomUUID()
        val batch = InteractionBatch(
            eventType = "user.interactions.batch",
            userId = userId,
            interactions = emptyList(),
            batchTs = Instant.now()
        )

        kafkaTemplate.send("user.interactions.batch", userId.toString(), batch).get()

        val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10))
        val record = records.first()
        assertThat(record.key()).isEqualTo(userId.toString())
    }

    @Test
    fun `message payload contains expected JSON fields`() {
        val userId = UUID.randomUUID()
        val contentId = UUID.randomUUID().toString()
        val now = Instant.now()
        val batch = InteractionBatch(
            eventType = "user.interactions.batch",
            userId = userId,
            interactions = listOf(
                InteractionEvent(
                    contentId = contentId,
                    actionType = "click",
                    durationSec = null,
                    timestamp = now.minusSeconds(5)
                )
            ),
            batchTs = now
        )

        kafkaTemplate.send("user.interactions.batch", userId.toString(), batch).get()

        val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10))
        val record = records.first()
        val json = objectMapper.readTree(record.value())

        assertThat(json.has("eventType") || json.has("event_type")).isTrue()
        assertThat(json.has("userId") || json.has("user_id")).isTrue()
        assertThat(json.has("interactions")).isTrue()
        assertThat(json.has("batchTs") || json.has("batch_ts")).isTrue()
    }
}
