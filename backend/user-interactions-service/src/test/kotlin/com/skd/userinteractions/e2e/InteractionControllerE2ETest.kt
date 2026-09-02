package com.skd.userinteractions.e2e

import com.fasterxml.jackson.databind.ObjectMapper
import com.skd.userinteractions.api.model.interactions.InteractionBatchRequest
import com.skd.userinteractions.api.model.interactions.InteractionBatchResponse
import com.skd.userinteractions.api.model.interactions.InteractionEvent
import com.skd.userinteractions.api.model.ErrorResponse
import com.skd.userinteractions.repository.UserInteractionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("e2e")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InteractionControllerE2ETest {

    companion object {
        private const val HMAC_SECRET = "test-hmac-secret"

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
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var repository: UserInteractionRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private fun computeHmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(HMAC_SECRET.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    private fun authenticatedHeaders(
        userId: String = UUID.randomUUID().toString(),
        roles: String = "USER",
        subscriptionTier: String = "FREE",
        requestId: String = UUID.randomUUID().toString()
    ): HttpHeaders {
        val payload = "$userId|$roles|$subscriptionTier|$requestId"
        return HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-User-Id", userId)
            set("X-User-Roles", roles)
            set("X-Subscription-Tier", subscriptionTier)
            set("X-Request-Id", requestId)
            set("X-Gateway-Signature", computeHmac(payload))
        }
    }

    private fun validEvent(
        contentId: String = UUID.randomUUID().toString(),
        actionType: String = "view",
        durationSec: Int? = null,
        timestamp: Instant = Instant.now().minusSeconds(30)
    ) = InteractionEvent(
        contentId = contentId,
        actionType = actionType,
        durationSec = durationSec,
        timestamp = timestamp
    )

    @Nested
    inner class `POST api interactions batch` {

        @Test
        fun `returns 202 Accepted for valid batch`() {
            val userId = UUID.randomUUID().toString()
            val headers = authenticatedHeaders(userId = userId)
            val request = InteractionBatchRequest(events = listOf(validEvent()))
            val entity = HttpEntity(request, headers)

            val response = restTemplate.postForEntity(
                "/api/interactions/batch",
                entity,
                InteractionBatchResponse::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
        }

        @Test
        fun `response body contains accepted and rejected counts`() {
            val userId = UUID.randomUUID().toString()
            val headers = authenticatedHeaders(userId = userId)
            val validEvent1 = validEvent(actionType = "view")
            val validEvent2 = validEvent(actionType = "click")
            val invalidEvent = validEvent(actionType = "invalid_action")
            val request = InteractionBatchRequest(events = listOf(validEvent1, validEvent2, invalidEvent))
            val entity = HttpEntity(request, headers)

            val response = restTemplate.postForEntity(
                "/api/interactions/batch",
                entity,
                InteractionBatchResponse::class.java
            )

            assertThat(response.body!!.accepted).isEqualTo(2)
            assertThat(response.body!!.rejected).isEqualTo(1)
        }

        @Test
        fun `persists accepted events in database`() {
            val userId = UUID.randomUUID()
            val headers = authenticatedHeaders(userId = userId.toString())
            val event = validEvent(actionType = "save")
            val request = InteractionBatchRequest(events = listOf(event))
            val entity = HttpEntity(request, headers)

            restTemplate.postForEntity(
                "/api/interactions/batch",
                entity,
                InteractionBatchResponse::class.java
            )

            val saved = repository.findByUserId(userId)
            assertThat(saved).hasSize(1)
            assertThat(saved[0].actionType).isEqualTo("BOOKMARK") // legacy "save" maps to canonical BOOKMARK
            assertThat(saved[0].userId).isEqualTo(userId)
        }

        @Test
        fun `persists scroll_depth and metadata from batch event`() {
            val userId = UUID.randomUUID()
            val headers = authenticatedHeaders(userId = userId.toString())
            val event = InteractionEvent(
                contentId = UUID.randomUUID().toString(),
                actionType = "view",
                durationSec = null,
                timestamp = Instant.now().minusSeconds(30),
                scrollDepth = 0.85,
                metadata = mapOf("source" to "e2e")
            )
            val request = InteractionBatchRequest(events = listOf(event))
            val entity = HttpEntity(request, headers)

            restTemplate.postForEntity(
                "/api/interactions/batch",
                entity,
                InteractionBatchResponse::class.java
            )

            val saved = repository.findByUserId(userId)
            assertThat(saved).hasSize(1)
            assertThat(saved[0].scrollDepth).isEqualTo(0.85f)
            assertThat(saved[0].metadata).isEqualTo(mapOf("source" to "e2e"))
        }

        @Test
        fun `returns 422 for batch exceeding max size`() {
            val userId = UUID.randomUUID().toString()
            val headers = authenticatedHeaders(userId = userId)
            val events = (1..101).map { validEvent() }
            val request = InteractionBatchRequest(events = events)
            val entity = HttpEntity(request, headers)

            val response = restTemplate.postForEntity(
                "/api/interactions/batch",
                entity,
                ErrorResponse::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            assertThat(response.body!!.error).isEqualTo("Unprocessable Entity")
        }

        @Test
        fun `returns 422 for empty batch`() {
            val userId = UUID.randomUUID().toString()
            val headers = authenticatedHeaders(userId = userId)
            val request = InteractionBatchRequest(events = emptyList())
            val entity = HttpEntity(request, headers)

            val response = restTemplate.postForEntity(
                "/api/interactions/batch",
                entity,
                ErrorResponse::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        }
    }

    @Nested
    inner class `HMAC authentication` {

        @Test
        fun `returns 401 without X-Gateway-Signature`() {
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-User-Id", UUID.randomUUID().toString())
            }
            val request = InteractionBatchRequest(events = listOf(validEvent()))
            val entity = HttpEntity(request, headers)

            val response = restTemplate.postForEntity(
                "/api/interactions/batch",
                entity,
                String::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `returns 401 with invalid HMAC signature`() {
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-User-Id", UUID.randomUUID().toString())
                set("X-User-Roles", "USER")
                set("X-Subscription-Tier", "FREE")
                set("X-Request-Id", UUID.randomUUID().toString())
                set("X-Gateway-Signature", "invalid-signature")
            }
            val request = InteractionBatchRequest(events = listOf(validEvent()))
            val entity = HttpEntity(request, headers)

            val response = restTemplate.postForEntity(
                "/api/interactions/batch",
                entity,
                String::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }
}
