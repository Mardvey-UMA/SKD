package com.skd.subscription.e2e

import com.skd.subscription.db.service.SubscriptionService
import com.skd.subscription.db.service.WebhookService
import com.skd.subscription.integration.rest.yookassa.YookassaClient
import com.skd.subscription.presentation.controller.YookassaWebhookController
import com.skd.subscription.presentation.filter.YookassaIpWhitelistFilter
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.*
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class YookassaWebhookControllerE2ETest {

    companion object {
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("subscription_db")
            .withUsername("test")
            .withPassword("test")

        private val kafka: KafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))

        init {
            postgres.start()
            kafka.start()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
            // Allow localhost (127.0.0.1) for tests since TestRestTemplate sends from loopback
            registry.add("yookassa.webhook.allowed-cidrs") { "127.0.0.1/32,185.71.76.0/27,185.71.77.0/27" }
        }
    }

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @MockkBean
    lateinit var webhookService: WebhookService

    @Nested
    inner class `POST webhook yookassa` {

        @Test
        fun `should return 200 and process payment succeeded webhook`() {
            every { webhookService.processWebhook(any(), any(), any()) } returns Unit

            val body = mapOf(
                "type" to "notification",
                "event" to "payment.succeeded",
                "object" to mapOf(
                    "id" to "yookassa-payment-123",
                    "status" to "succeeded",
                    "amount" to mapOf("value" to "299.00", "currency" to "RUB")
                )
            )
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }

            val response = restTemplate.exchange(
                "/webhook/yookassa",
                HttpMethod.POST,
                HttpEntity(body, headers),
                Map::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            verify(exactly = 1) {
                webhookService.processWebhook(
                    any(),
                    eq("payment.succeeded"),
                    eq("yookassa-payment-123")
                )
            }
        }

        @Test
        fun `should return 200 and process payment canceled webhook`() {
            every { webhookService.processWebhook(any(), any(), any()) } returns Unit

            val body = mapOf(
                "type" to "notification",
                "event" to "payment.canceled",
                "object" to mapOf(
                    "id" to "yookassa-payment-456",
                    "status" to "canceled",
                    "amount" to mapOf("value" to "299.00", "currency" to "RUB")
                )
            )
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }

            val response = restTemplate.exchange(
                "/webhook/yookassa",
                HttpMethod.POST,
                HttpEntity(body, headers),
                Map::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            verify(exactly = 1) {
                webhookService.processWebhook(
                    any(),
                    eq("payment.canceled"),
                    eq("yookassa-payment-456")
                )
            }
        }

        @Test
        fun `should not require HMAC signature on webhook endpoint`() {
            // Webhook does NOT need X-Gateway-Signature (it uses IP whitelist instead)
            every { webhookService.processWebhook(any(), any(), any()) } returns Unit

            val body = mapOf(
                "type" to "notification",
                "event" to "payment.succeeded",
                "object" to mapOf(
                    "id" to "yookassa-payment-789",
                    "status" to "succeeded",
                    "amount" to mapOf("value" to "100.00", "currency" to "RUB")
                )
            )
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                // Deliberately no X-Gateway-Signature headers
            }

            val response = restTemplate.exchange(
                "/webhook/yookassa",
                HttpMethod.POST,
                HttpEntity(body, headers),
                Map::class.java
            )

            // Should be accepted (200) because webhook path is excluded from HMAC filter
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        }

        @Test
        fun `should return 200 idempotently for duplicate webhook with same event`() {
            every { webhookService.processWebhook(any(), any(), any()) } returns Unit

            val body = mapOf(
                "type" to "notification",
                "event" to "payment.succeeded",
                "object" to mapOf(
                    "id" to "yookassa-payment-idempotent",
                    "status" to "succeeded",
                    "amount" to mapOf("value" to "299.00", "currency" to "RUB")
                )
            )
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }

            // First call
            val response1 = restTemplate.exchange(
                "/webhook/yookassa",
                HttpMethod.POST,
                HttpEntity(body, headers),
                Map::class.java
            )
            assertThat(response1.statusCode).isEqualTo(HttpStatus.OK)

            // Second call with same payload (idempotent)
            val response2 = restTemplate.exchange(
                "/webhook/yookassa",
                HttpMethod.POST,
                HttpEntity(body, headers),
                Map::class.java
            )
            assertThat(response2.statusCode).isEqualTo(HttpStatus.OK)
        }
    }
}
