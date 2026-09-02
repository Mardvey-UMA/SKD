package com.skd.subscription.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.repository.payment.model.Payment
import com.skd.subscription.processor.SubscriptionProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.util.UUID

@Tag("integration")
class SubscriptionProcessorLazyReconcileIntegrationTest : IntegrationTestBase() {

    companion object {
        private val wireMock: WireMockServer = WireMockServer(wireMockConfig().dynamicPort())

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
        fun configureWireMockPort(registry: DynamicPropertyRegistry) {
            if (!wireMock.isRunning) {
                wireMock.start()
            }
            registry.add("wiremock.server.port") { wireMock.port() }
        }
    }

    @Autowired
    lateinit var subscriptionProcessor: SubscriptionProcessor

    @Autowired
    lateinit var paymentRepository: PaymentRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun resetState() {
        wireMock.resetAll()
        jdbcTemplate.execute("DELETE FROM outbox")
        jdbcTemplate.execute("DELETE FROM saved_payment_methods")
        jdbcTemplate.execute("DELETE FROM subscriptions")
        jdbcTemplate.execute("DELETE FROM payments")
    }

    @Test
    fun `getStatus with pending payment and YooKassa succeeded transitions to premium active and persists subscription plus outbox`() {
        val userId = UUID.randomUUID()
        val externalId = "yk-lazy-succeeded-1"
        seedPendingPayment(userId, externalId)
        stubYookassaGetSucceeded(externalId)

        val result = subscriptionProcessor.getStatus(userId)

        assertThat(result.tier)
            .`as`("lazy reconcile must upgrade tier to premium when YooKassa confirms succeeded")
            .isEqualTo("premium")
        assertThat(result.status)
            .`as`("subscription status returned to caller must reflect the applied reconciliation")
            .isEqualTo("active")

        val paymentStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM payments WHERE external_id = ?",
            String::class.java,
            externalId
        )
        assertThat(paymentStatus)
            .`as`("pending payment must be marked succeeded in DB after lazy reconcile")
            .isEqualTo("succeeded")

        val subscriptionCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM subscriptions WHERE user_id = ?",
            Int::class.java,
            userId
        )
        assertThat(subscriptionCount)
            .`as`("a subscription row must be created for the user after lazy reconcile")
            .isEqualTo(1)

        val outboxCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox WHERE event_type = 'subscription.changed' AND aggregate_id = ?",
            Int::class.java,
            userId.toString()
        )
        assertThat(outboxCount)
            .`as`("subscription.changed outbox event must be emitted exactly once")
            .isEqualTo(1)
    }

    @Test
    fun `getStatus with no pending payment returns free and performs zero YooKassa calls`() {
        val userId = UUID.randomUUID()

        val result = subscriptionProcessor.getStatus(userId)

        assertThat(result.tier).isEqualTo("free")
        assertThat(result.status).isEqualTo("none")

        wireMock.verify(
            0,
            getRequestedFor(urlMatching("/v3/payments/.*"))
        )
    }

    @Test
    fun `getStatus still returns free when YooKassa returns 500 for pending payment (fail-safe)`() {
        val userId = UUID.randomUUID()
        val externalId = "yk-lazy-500-1"
        seedPendingPayment(userId, externalId)
        stubYookassaGet500(externalId)

        val result = subscriptionProcessor.getStatus(userId)

        assertThat(result.tier)
            .`as`("lazy reconcile failures must NEVER bubble up to the caller — degrade to free")
            .isEqualTo("free")
        assertThat(result.status).isEqualTo("none")

        val paymentStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM payments WHERE external_id = ?",
            String::class.java,
            externalId
        )
        assertThat(paymentStatus)
            .`as`("pending payment must remain pending after YooKassa 500")
            .isEqualTo("pending")

        val subscriptionCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM subscriptions WHERE user_id = ?",
            Int::class.java,
            userId
        )
        assertThat(subscriptionCount).isEqualTo(0)
    }

    // --- Helpers ---

    private fun seedPendingPayment(userId: UUID, externalId: String): Payment {
        val now = Instant.now()
        return paymentRepository.save(
            Payment(
                userId = userId,
                planId = "premium_monthly",
                amountKopecks = 29900,
                status = "pending",
                externalId = externalId,
                idempotencyKey = UUID.randomUUID(),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun stubYookassaGetSucceeded(externalId: String) {
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
    }

    private fun stubYookassaGet500(externalId: String) {
        wireMock.stubFor(
            get(urlPathEqualTo("/v3/payments/$externalId"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"type":"error","code":"internal_server_error"}""")
                )
        )
    }
}
