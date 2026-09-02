package com.skd.userinteractions.integration

import com.skd.userinteractions.api.model.interactions.InteractionEvent
import com.skd.userinteractions.application.InteractionBuffer
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
class InteractionBufferIntegrationTest {

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
    lateinit var buffer: InteractionBuffer

    private lateinit var consumer: org.apache.kafka.clients.consumer.Consumer<String, String>

    private fun testEvent(
        contentId: String = UUID.randomUUID().toString(),
        actionType: String = "view"
    ) = InteractionEvent(
        contentId = contentId,
        actionType = actionType,
        durationSec = null,
        timestamp = Instant.now().minusSeconds(10)
    )

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
            consumer.poll(Duration.ofMillis(100))
        }
    }

    @AfterEach
    fun tearDownConsumer() {
        consumer.close()
    }

    @Test
    fun `buffer is injected from Spring context`() {
        assertThat(buffer).isNotNull
    }

    @Test
    fun `threshold flush publishes to Kafka when max events per user reached`() {
        val userId = UUID.randomUUID()
        // Test profile has maxEventsPerUser = 5
        val events = (1..5).map { testEvent() }

        buffer.addEvents(userId, events)

        val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10))
        assertThat(records.count()).isGreaterThan(0)
        assertThat(records.map { it.key() }).contains(userId.toString())
        assertThat(records.first { it.key() == userId.toString() }.topic()).isEqualTo("user.interactions.batch")
    }

    @Test
    fun `flushAll publishes remaining buffered events to Kafka`() {
        val userId = UUID.randomUUID()
        // Add below threshold (less than 5)
        buffer.addEvents(userId, listOf(testEvent(), testEvent()))

        buffer.flushAll()

        val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10))
        assertThat(records.count()).isGreaterThan(0)
        assertThat(records.map { it.key() }).contains(userId.toString())
    }

    @Test
    fun `multiple users are flushed independently`() {
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()

        buffer.addEvents(userId1, listOf(testEvent()))
        buffer.addEvents(userId2, listOf(testEvent(), testEvent()))

        buffer.flushAll()

        // Poll multiple times to collect all records (two users = potentially two sends)
        val allKeys = mutableSetOf<String>()
        val deadline = System.currentTimeMillis() + 15_000L
        while (allKeys.size < 2 && System.currentTimeMillis() < deadline) {
            val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2))
            allKeys.addAll(records.map { it.key() })
        }

        assertThat(allKeys).contains(userId1.toString())
        assertThat(allKeys).contains(userId2.toString())
    }
}
