package com.skd.subscription.e2e

import com.skd.subscription.db.service.SubscriptionService
import com.skd.subscription.db.service.WebhookService
import com.skd.subscription.integration.rest.yookassa.PaymentProviderUnavailableException
import com.skd.subscription.integration.rest.yookassa.YookassaClient
import com.skd.subscription.presentation.GlobalExceptionHandler
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GlobalExceptionHandlerE2ETest {

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
        }

        private const val HMAC_SECRET = "test-hmac-secret-for-integration-tests"

        private val MAP_TYPE = object : ParameterizedTypeReference<Map<String, Any?>>() {}

        fun computeHmac(data: String, secret: String = HMAC_SECRET): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray()))
        }
    }

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @MockkBean
    lateinit var subscriptionService: SubscriptionService

    private fun authenticatedHeaders(
        userId: String = UUID.randomUUID().toString(),
        roles: String = "ROLE_USER",
        tier: String = "free",
        requestId: String = UUID.randomUUID().toString()
    ): HttpHeaders {
        val payload = "$userId|$roles|$tier|$requestId"
        val signature = computeHmac(payload)
        return HttpHeaders().apply {
            set("X-Gateway-Signature", signature)
            set("X-User-Id", userId)
            set("X-User-Roles", roles)
            set("X-Subscription-Tier", tier)
            set("X-Request-Id", requestId)
            contentType = MediaType.APPLICATION_JSON
        }
    }

    @Nested
    inner class `error response format` {

        @Test
        fun `should return standard error format when IllegalArgumentException is thrown`() {
            val userId = UUID.randomUUID()
            every { subscriptionService.checkout(userId, "nonexistent_plan") } throws
                IllegalArgumentException("Plan not found or inactive: nonexistent_plan")

            val headers = authenticatedHeaders(userId = userId.toString())
            val body = mapOf("plan" to "nonexistent_plan")
            val response = restTemplate.exchange(
                "/api/subscription/checkout",
                HttpMethod.POST,
                HttpEntity(body, headers),
                MAP_TYPE
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            val responseBody = response.body!!
            assertThat(responseBody).containsKey("error")
            assertThat(responseBody).containsKey("message")
            assertThat(responseBody).containsKey("timestamp")
            assertThat(responseBody["message"]).isEqualTo("Plan not found or inactive: nonexistent_plan")
        }

        @Test
        fun `should return standard error format when IllegalStateException is thrown`() {
            val userId = UUID.randomUUID()
            every { subscriptionService.cancel(userId) } throws
                IllegalStateException("No subscription found for userId=$userId")

            val headers = authenticatedHeaders(userId = userId.toString())
            val response = restTemplate.exchange(
                "/api/subscription/cancel",
                HttpMethod.POST,
                HttpEntity<Void>(headers),
                MAP_TYPE
            )

            // IllegalStateException should map to 409 Conflict or 400 Bad Request
            assertThat(response.statusCode.is4xxClientError).isTrue()
            val responseBody = response.body!!
            assertThat(responseBody).containsKey("error")
            assertThat(responseBody).containsKey("message")
            assertThat(responseBody).containsKey("timestamp")
        }

        @Test
        fun `should include request_id in error response when X-Request-Id header is present`() {
            val userId = UUID.randomUUID()
            val requestId = UUID.randomUUID().toString()
            every { subscriptionService.getStatus(userId) } throws RuntimeException("Unexpected error")

            val headers = authenticatedHeaders(userId = userId.toString(), requestId = requestId)
            val response = restTemplate.exchange(
                "/api/subscription/status",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                MAP_TYPE
            )

            assertThat(response.statusCode.is5xxServerError).isTrue()
            val responseBody = response.body!!
            assertThat(responseBody).containsKey("error")
            assertThat(responseBody).containsKey("message")
            assertThat(responseBody).containsKey("request_id")
            assertThat(responseBody).containsKey("timestamp")
            assertThat(responseBody["request_id"]).isEqualTo(requestId)
        }
    }

    @Nested
    inner class `payment provider unavailable` {

        @Test
        fun `should return 503 when PaymentProviderUnavailableException is thrown from checkout`() {
            val userId = UUID.randomUUID()
            every { subscriptionService.checkout(userId, "premium_monthly") } throws
                PaymentProviderUnavailableException(RuntimeException("YooKassa down"))

            val headers = authenticatedHeaders(userId = userId.toString())
            val body = mapOf("plan" to "premium_monthly")
            val response = restTemplate.exchange(
                "/api/subscription/checkout",
                HttpMethod.POST,
                HttpEntity(body, headers),
                MAP_TYPE
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        }

        @Test
        fun `should return standard error body with payment_provider_unavailable code`() {
            val userId = UUID.randomUUID()
            val requestId = UUID.randomUUID().toString()
            every { subscriptionService.checkout(userId, "premium_monthly") } throws
                PaymentProviderUnavailableException(RuntimeException("YooKassa 503"))

            val headers = authenticatedHeaders(userId = userId.toString(), requestId = requestId)
            val body = mapOf("plan" to "premium_monthly")
            val response = restTemplate.exchange(
                "/api/subscription/checkout",
                HttpMethod.POST,
                HttpEntity(body, headers),
                MAP_TYPE
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            val responseBody = response.body!!
            assertThat(responseBody["error"]).isEqualTo("payment_provider_unavailable")
            assertThat(responseBody["message"]).isEqualTo("Платёжный провайдер временно недоступен")
            assertThat(responseBody["request_id"]).isEqualTo(requestId)
            assertThat(responseBody).containsKey("timestamp")
        }

        @Test
        fun `should return 503 with russian message regardless of underlying cause`() {
            val userId = UUID.randomUUID()
            every { subscriptionService.checkout(userId, "premium_yearly") } throws
                PaymentProviderUnavailableException(
                    java.net.SocketTimeoutException("Read timed out")
                )

            val headers = authenticatedHeaders(userId = userId.toString())
            val body = mapOf("plan" to "premium_yearly")
            val response = restTemplate.exchange(
                "/api/subscription/checkout",
                HttpMethod.POST,
                HttpEntity(body, headers),
                MAP_TYPE
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            assertThat(response.body!!["error"]).isEqualTo("payment_provider_unavailable")
            assertThat(response.body!!["message"]).isEqualTo("Платёжный провайдер временно недоступен")
        }
    }
}
