package com.skd.userinteractions.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.skd.userinteractions.api.model.interactions.InteractionBatchRequest
import com.skd.userinteractions.api.model.interactions.InteractionBatchResponse
import com.skd.userinteractions.api.model.interactions.InteractionEvent
import com.skd.userinteractions.application.InteractionBuffer
import com.skd.userinteractions.repository.UserInteractionRepository
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InteractionBatchV2IntegrationTest {

    companion object {
        private const val HMAC_SECRET = "test-hmac-secret"

        @Container @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("interactions_db")
            .withUsername("test")
            .withPassword("test")

        @Container @JvmStatic
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))

        @DynamicPropertySource @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
        }
    }

    @Autowired lateinit var restTemplate: TestRestTemplate
    @Autowired lateinit var repository: UserInteractionRepository
    @Autowired lateinit var buffer: InteractionBuffer
    @Autowired lateinit var objectMapper: ObjectMapper

    private lateinit var consumer: org.apache.kafka.clients.consumer.Consumer<String, String>

    private fun computeHmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(HMAC_SECRET.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    private fun authenticatedHeaders(userId: String = UUID.randomUUID().toString()): HttpHeaders {
        val roles = "USER"
        val tier = "FREE"
        val requestId = UUID.randomUUID().toString()
        return HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-User-Id", userId)
            set("X-User-Roles", roles)
            set("X-Subscription-Tier", tier)
            set("X-Request-Id", requestId)
            set("X-Gateway-Signature", computeHmac("$userId|$roles|$tier|$requestId"))
        }
    }

    @BeforeEach
    fun setUpConsumer() {
        val consumerProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "test-v2-group-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java
        )
        consumer = DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()
        consumer.subscribe(listOf("user.interactions.batch"))
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
    fun `v2 batch - new fields persisted to DB and Kafka message has schema_version 2 with new event fields`() {
        val userId = UUID.randomUUID()
        val feedReqId = UUID.randomUUID()
        val headers = authenticatedHeaders(userId = userId.toString())

        val event = InteractionEvent(
            contentId = UUID.randomUUID().toString(),
            actionType = "click",
            durationSec = 45,
            timestamp = Instant.now().minusSeconds(30),
            feedRequestId = feedReqId.toString(),
            positionInFeed = 2,
            deviceType = "android",
            appVersion = "1.2.3",
            abBucket = 1
        )
        val request = InteractionBatchRequest(events = listOf(event))
        val entity = HttpEntity(request, headers)

        val response = restTemplate.postForEntity(
            "/api/interactions/batch", entity, InteractionBatchResponse::class.java
        )
        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
        assertThat(response.body!!.accepted).isEqualTo(1)

        val saved = repository.findByUserId(userId)
        assertThat(saved).hasSize(1)
        val row = saved[0]
        assertThat(row.feedRequestId).isEqualTo(feedReqId)
        assertThat(row.positionInFeed).isEqualTo(2.toShort())
        assertThat(row.deviceType).isEqualTo("android")
        assertThat(row.appVersion).isEqualTo("1.2.3")
        assertThat(row.abBucket).isEqualTo(1.toShort())

        buffer.flushAll()

        val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10))
        val record = records.first { it.key() == userId.toString() }
        val json = objectMapper.readTree(record.value())

        assertThat(json.get("schema_version").asInt()).isEqualTo(2)
        val interaction = json.get("interactions")[0]
        assertThat(interaction.get("feed_request_id").asText()).isEqualTo(feedReqId.toString())
        assertThat(interaction.get("position_in_feed").asInt()).isEqualTo(2)
        assertThat(interaction.get("device_type").asText()).isEqualTo("android")
        assertThat(interaction.get("app_version").asText()).isEqualTo("1.2.3")
        assertThat(interaction.get("ab_bucket").asInt()).isEqualTo(1)
    }

    @Test
    fun `v1 batch - new DB columns are null and Kafka message still has schema_version 2 with new fields absent`() {
        val userId = UUID.randomUUID()
        val headers = authenticatedHeaders(userId = userId.toString())

        val event = InteractionEvent(
            contentId = UUID.randomUUID().toString(),
            actionType = "view",
            durationSec = null,
            timestamp = Instant.now().minusSeconds(30)
        )
        val request = InteractionBatchRequest(events = listOf(event))
        val entity = HttpEntity(request, headers)

        val response = restTemplate.postForEntity(
            "/api/interactions/batch", entity, InteractionBatchResponse::class.java
        )
        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)

        val saved = repository.findByUserId(userId)
        assertThat(saved).hasSize(1)
        val row = saved[0]
        assertThat(row.feedRequestId).isNull()
        assertThat(row.positionInFeed).isNull()
        assertThat(row.deviceType).isNull()
        assertThat(row.appVersion).isNull()
        assertThat(row.abBucket).isEqualTo(0.toShort())

        buffer.flushAll()

        val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10))
        val record = records.first { it.key() == userId.toString() }
        val json = objectMapper.readTree(record.value())

        assertThat(json.get("schema_version").asInt()).isEqualTo(2)
        val interaction = json.get("interactions")[0]
        assertThat(interaction.has("feed_request_id")).isFalse()
        assertThat(interaction.has("position_in_feed")).isFalse()
        assertThat(interaction.has("device_type")).isFalse()
        assertThat(interaction.has("app_version")).isFalse()
        assertThat(interaction.has("ab_bucket")).isFalse()
    }

    @Test
    fun `v2 batch with scroll_depth and metadata - fields deserialize correctly`() {
        val userId = UUID.randomUUID()
        val headers = authenticatedHeaders(userId = userId.toString())

        val event = InteractionEvent(
            contentId = UUID.randomUUID().toString(),
            actionType = "view",
            durationSec = 120,
            timestamp = Instant.now().minusSeconds(30),
            scrollDepth = 0.85,
            metadata = mapOf("source" to "feed_card")
        )
        val request = InteractionBatchRequest(events = listOf(event))
        val entity = HttpEntity(request, headers)

        val response = restTemplate.postForEntity(
            "/api/interactions/batch", entity, InteractionBatchResponse::class.java
        )
        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)

        buffer.flushAll()

        val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10))
        val record = records.first { it.key() == userId.toString() }
        val json = objectMapper.readTree(record.value())
        val interaction = json.get("interactions")[0]
        assertThat(interaction.get("scroll_depth").asDouble()).isEqualTo(0.85)
        assertThat(interaction.get("metadata").get("source").asText()).isEqualTo("feed_card")
    }

    @Test
    fun `legacy SAVE action is persisted as canonical BOOKMARK in DB`() {
        val userId = UUID.randomUUID()
        val headers = authenticatedHeaders(userId = userId.toString())

        val event = InteractionEvent(
            contentId = UUID.randomUUID().toString(),
            actionType = "SAVE",
            durationSec = null,
            timestamp = Instant.now().minusSeconds(30)
        )
        val request = InteractionBatchRequest(events = listOf(event))
        val entity = HttpEntity(request, headers)

        val response = restTemplate.postForEntity(
            "/api/interactions/batch", entity, InteractionBatchResponse::class.java
        )
        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
        assertThat(response.body!!.accepted).isEqualTo(1)

        val saved = repository.findByUserId(userId)
        assertThat(saved).hasSize(1)
        assertThat(saved[0].actionType).isEqualTo("BOOKMARK")
        buffer.flushAll()
    }

    @Test
    fun `canonical LIKE action is persisted as LIKE in DB`() {
        val userId = UUID.randomUUID()
        val headers = authenticatedHeaders(userId = userId.toString())

        val event = InteractionEvent(
            contentId = UUID.randomUUID().toString(),
            actionType = "LIKE",
            durationSec = null,
            timestamp = Instant.now().minusSeconds(30)
        )
        val request = InteractionBatchRequest(events = listOf(event))
        val entity = HttpEntity(request, headers)

        val response = restTemplate.postForEntity(
            "/api/interactions/batch", entity, InteractionBatchResponse::class.java
        )
        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
        assertThat(response.body!!.accepted).isEqualTo(1)

        val saved = repository.findByUserId(userId)
        assertThat(saved).hasSize(1)
        assertThat(saved[0].actionType).isEqualTo("LIKE")
        buffer.flushAll()
    }

    @Test
    fun `legacy SAVE action emits canonical BOOKMARK in Kafka payload`() {
        val userId = UUID.randomUUID()
        val headers = authenticatedHeaders(userId = userId.toString())

        val event = InteractionEvent(
            contentId = UUID.randomUUID().toString(),
            actionType = "SAVE",
            durationSec = null,
            timestamp = Instant.now().minusSeconds(30)
        )
        val request = InteractionBatchRequest(events = listOf(event))
        val entity = HttpEntity(request, headers)

        restTemplate.postForEntity("/api/interactions/batch", entity, InteractionBatchResponse::class.java)

        buffer.flushAll()

        val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10))
        val record = records.first { it.key() == userId.toString() }
        val json = objectMapper.readTree(record.value())
        val interaction = json.get("interactions")[0]
        assertThat(interaction.get("action_type").asText()).isEqualTo("BOOKMARK")
    }
}
