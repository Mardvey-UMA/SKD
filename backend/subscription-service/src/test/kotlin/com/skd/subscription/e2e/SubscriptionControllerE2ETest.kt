package com.skd.subscription.e2e

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.repository.payment.model.Payment
import com.skd.subscription.db.service.CheckoutResult
import com.skd.subscription.db.service.SubscriptionService
import com.skd.subscription.db.service.SubscriptionStatusResult
import com.skd.subscription.db.service.WebhookService
import com.skd.subscription.integration.rest.yookassa.YookassaClient
import com.skd.subscription.presentation.controller.SubscriptionController
import com.skd.subscription.presentation.filter.GatewaySignatureFilter
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SubscriptionControllerE2ETest {

    companion object {
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("subscription_db")
            .withUsername("test")
            .withPassword("test")

        private val kafka: KafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))

        private val wireMock: WireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        init {
            postgres.start()
            kafka.start()
        }

        @BeforeAll
        @JvmStatic
        fun startWireMock() {
            if (!wireMock.isRunning) {
                wireMock.start()
            }
        }

        @AfterAll
        @JvmStatic
        fun stopWireMock() {
            wireMock.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
            if (!wireMock.isRunning) {
                wireMock.start()
            }
            registry.add("wiremock.server.port") { wireMock.port() }
        }

        private const val HMAC_SECRET = "test-hmac-secret-for-integration-tests"

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
    inner class `GET subscription status` {

        @Test
        fun `should return current subscription status with valid HMAC`() {
            val userId = UUID.randomUUID()
            val expiresAt = Instant.now().plusSeconds(86400 * 29L)
            every { subscriptionService.getStatus(userId) } returns SubscriptionStatusResult(
                tier = "premium",
                status = "active",
                planId = "premium_monthly",
                expiresAt = expiresAt,
                autoRenew = true
            )

            val headers = authenticatedHeaders(userId = userId.toString())
            val response = restTemplate.exchange(
                "/api/subscription/status",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                Map::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body!!["tier"]).isEqualTo("premium")
            assertThat(response.body!!["status"]).isEqualTo("active")
            assertThat(response.body!!["planId"]).isEqualTo("premium_monthly")
            assertThat(response.body!!["autoRenew"]).isEqualTo(true)
            verify(exactly = 1) { subscriptionService.getStatus(userId) }
        }

        @Test
        fun `should return free tier when user has no subscription`() {
            val userId = UUID.randomUUID()
            every { subscriptionService.getStatus(userId) } returns SubscriptionStatusResult(
                tier = "free",
                status = "none",
                planId = null,
                expiresAt = null,
                autoRenew = false
            )

            val headers = authenticatedHeaders(userId = userId.toString())
            val response = restTemplate.exchange(
                "/api/subscription/status",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                Map::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body!!["tier"]).isEqualTo("free")
            assertThat(response.body!!["status"]).isEqualTo("none")
        }

        @Test
        fun `should return 401 when X-Gateway-Signature is missing`() {
            val headers = HttpHeaders().apply {
                set("X-User-Id", UUID.randomUUID().toString())
            }
            val response = restTemplate.exchange(
                "/api/subscription/status",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                Map::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `should return 401 when X-Gateway-Signature is invalid`() {
            val headers = HttpHeaders().apply {
                set("X-Gateway-Signature", "invalid-signature")
                set("X-User-Id", UUID.randomUUID().toString())
                set("X-User-Roles", "ROLE_USER")
                set("X-Subscription-Tier", "free")
                set("X-Request-Id", UUID.randomUUID().toString())
            }
            val response = restTemplate.exchange(
                "/api/subscription/status",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                Map::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @Nested
    inner class `POST checkout` {

        @Test
        fun `should create payment and return confirmation url`() {
            val userId = UUID.randomUUID()
            val paymentId = UUID.randomUUID().toString()
            val confirmationUrl = "https://yookassa.ru/checkout/abc"

            every { subscriptionService.checkout(userId, "premium_monthly") } returns CheckoutResult(
                paymentId = paymentId,
                confirmationUrl = confirmationUrl
            )

            val headers = authenticatedHeaders(userId = userId.toString())
            val body = mapOf("plan" to "premium_monthly")
            val response = restTemplate.exchange(
                "/api/subscription/checkout",
                HttpMethod.POST,
                HttpEntity(body, headers),
                Map::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body!!["paymentId"]).isEqualTo(paymentId)
            assertThat(response.body!!["confirmationUrl"]).isEqualTo(confirmationUrl)
            verify(exactly = 1) { subscriptionService.checkout(userId, "premium_monthly") }
        }

        @Test
        fun `should return 401 when not authenticated`() {
            val body = mapOf("plan" to "premium_monthly")
            val response = restTemplate.postForEntity(
                "/api/subscription/checkout",
                body,
                Map::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @Nested
    inner class `POST cancel` {

        @Test
        fun `should cancel subscription and return 200`() {
            val userId = UUID.randomUUID()
            every { subscriptionService.cancel(userId) } returns Unit

            val headers = authenticatedHeaders(userId = userId.toString())
            val response = restTemplate.exchange(
                "/api/subscription/cancel",
                HttpMethod.POST,
                HttpEntity<Void>(headers),
                Map::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            verify(exactly = 1) { subscriptionService.cancel(userId) }
        }
    }

    @Nested
    inner class `GET subscription status with lazy reconcile` {

        @Autowired
        lateinit var paymentRepository: PaymentRepository

        @Autowired
        lateinit var jdbcTemplate: JdbcTemplate

        @BeforeEach
        fun cleanDbAndWireMock() {
            wireMock.resetAll()
            jdbcTemplate.execute("DELETE FROM outbox")
            jdbcTemplate.execute("DELETE FROM saved_payment_methods")
            jdbcTemplate.execute("DELETE FROM subscriptions")
            jdbcTemplate.execute("DELETE FROM payments")
        }

        @Test
        fun `GET subscription status with pending payment lazily reconciles via YooKassa and returns premium`() {
            val userId = UUID.randomUUID()
            val externalId = "yk-e2e-lazy-reconcile-1"
            val expiresAt = Instant.now().plusSeconds(30L * 86400L)

            // Seed pending payment directly into DB (user has no active subscription)
            paymentRepository.save(
                Payment(
                    userId = userId,
                    planId = "premium_monthly",
                    amountKopecks = 29900,
                    status = "pending",
                    externalId = externalId,
                    idempotencyKey = UUID.randomUUID(),
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            )

            // WireMock returns succeeded for GET /v3/payments/{externalId}
            wireMock.stubFor(
                get(urlPathEqualTo("/v3/payments/$externalId"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                                {
                                    "id": "$externalId",
                                    "status": "succeeded",
                                    "amount": {"value": "299.00", "currency": "RUB"}
                                }
                                """.trimIndent()
                            )
                    )
            )

            // SubscriptionService is @MockkBean — processor calls getStatus twice:
            // 1st call: returns "none" to drive lazy reconcile branch
            // 2nd call (after reconcile): returns premium/active reflecting real DB state
            every { subscriptionService.getStatus(userId) } returnsMany listOf(
                SubscriptionStatusResult(
                    tier = "free",
                    status = "none",
                    planId = null,
                    expiresAt = null,
                    autoRenew = false
                ),
                SubscriptionStatusResult(
                    tier = "premium",
                    status = "active",
                    planId = "premium_monthly",
                    expiresAt = expiresAt,
                    autoRenew = true
                )
            )

            val headers = authenticatedHeaders(userId = userId.toString())
            val response = restTemplate.exchange(
                "/api/subscription/status",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                Map::class.java
            )

            // HTTP response: 200 with premium/active
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body!!["tier"])
                .`as`("lazy reconcile must upgrade tier to premium end-to-end")
                .isEqualTo("premium")
            assertThat(response.body!!["status"])
                .`as`("lazy reconcile must mark subscription active end-to-end")
                .isEqualTo("active")

            // DB: payment row transitioned from pending to succeeded
            val paymentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE external_id = ?",
                String::class.java,
                externalId
            )
            assertThat(paymentStatus)
                .`as`("real PaymentReconciler must persist payment status=succeeded")
                .isEqualTo("succeeded")

            // DB: subscription row now exists with status=active
            val subscriptionStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM subscriptions WHERE user_id = ?",
                String::class.java,
                userId
            )
            assertThat(subscriptionStatus)
                .`as`("real PaymentReconciler must create subscription row with status=active")
                .isEqualTo("active")

            // DB: outbox has subscription.changed event for this user
            val outboxCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox WHERE event_type = 'subscription.changed' AND aggregate_id = ?",
                Int::class.java,
                userId.toString()
            )
            assertThat(outboxCount)
                .`as`("subscription.changed outbox event must be emitted exactly once by reconciler")
                .isEqualTo(1)
        }
    }
}
