package com.skd.subscription.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.repository.payment.model.Payment
import com.skd.subscription.db.repository.subscription.SubscriptionRepository
import com.skd.subscription.db.repository.subscription.model.Subscription
import com.skd.subscription.db.service.PaymentReconciler
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
class PaymentReconcilerIntegrationTest : IntegrationTestBase() {

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
    lateinit var paymentReconciler: PaymentReconciler

    @Autowired
    lateinit var paymentRepository: PaymentRepository

    @Autowired
    lateinit var subscriptionRepository: SubscriptionRepository

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
    fun `succeeded YooKassa response transitions pending payment to succeeded, creates subscription and one outbox row`() {
        val userId = UUID.randomUUID()
        val externalId = "yk-succeeded-1"
        val saved = seedPendingPayment(userId, externalId)

        stubYookassaGet(externalId, "succeeded")

        val outcome = paymentReconciler.fetchAndApply(saved, PaymentReconciler.Trigger.WEBHOOK)

        assertThat(outcome).isEqualTo(PaymentReconciler.Outcome.SUCCEEDED_APPLIED)

        val paymentStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM payments WHERE id = ?", String::class.java, saved.id
        )
        assertThat(paymentStatus).isEqualTo("succeeded")

        val externalStatus = jdbcTemplate.queryForObject(
            "SELECT external_status FROM payments WHERE id = ?", String::class.java, saved.id
        )
        assertThat(externalStatus).isEqualTo("succeeded")

        val subscriptionCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM subscriptions WHERE user_id = ?", Int::class.java, userId
        )
        assertThat(subscriptionCount).isEqualTo(1)

        val subscriptionStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM subscriptions WHERE user_id = ?", String::class.java, userId
        )
        assertThat(subscriptionStatus).isEqualTo("active")

        val subscriptionTier = jdbcTemplate.queryForObject(
            "SELECT tier FROM subscriptions WHERE user_id = ?", String::class.java, userId
        )
        assertThat(subscriptionTier).isEqualTo("premium")

        val outboxCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox WHERE event_type = 'subscription.changed' AND aggregate_id = ?",
            Int::class.java,
            userId.toString()
        )
        assertThat(outboxCount).isEqualTo(1)

        val outboxPayload = jdbcTemplate.queryForObject(
            "SELECT payload FROM outbox WHERE aggregate_id = ? LIMIT 1",
            String::class.java,
            userId.toString()
        )
        assertThat(outboxPayload).contains("\"status\":\"active\"")
        assertThat(outboxPayload).contains("\"tier\":\"premium\"")
    }

    @Test
    fun `succeeded response upserts existing subscription without creating duplicate row`() {
        val userId = UUID.randomUUID()
        val externalId = "yk-upsert-1"
        val saved = seedPendingPayment(userId, externalId)
        seedExistingExpiredSubscription(userId)

        stubYookassaGet(externalId, "succeeded")

        val outcome = paymentReconciler.fetchAndApply(saved, PaymentReconciler.Trigger.WEBHOOK)

        assertThat(outcome).isEqualTo(PaymentReconciler.Outcome.SUCCEEDED_APPLIED)

        val subscriptionCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM subscriptions WHERE user_id = ?", Int::class.java, userId
        )
        assertThat(subscriptionCount)
            .`as`("must upsert existing subscription rather than create a duplicate")
            .isEqualTo(1)

        val subscriptionStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM subscriptions WHERE user_id = ?", String::class.java, userId
        )
        assertThat(subscriptionStatus).isEqualTo("active")

        val expiresAt = jdbcTemplate.queryForObject(
            "SELECT expires_at FROM subscriptions WHERE user_id = ?", java.sql.Timestamp::class.java, userId
        )!!
        assertThat(expiresAt.toInstant()).isAfter(Instant.now())
    }

    @Test
    fun `invoking fetchAndApply twice leaves DB identical to a single call (idempotent)`() {
        val userId = UUID.randomUUID()
        val externalId = "yk-idem-1"
        val saved = seedPendingPayment(userId, externalId)

        stubYookassaGet(externalId, "succeeded")

        val first = paymentReconciler.fetchAndApply(saved, PaymentReconciler.Trigger.WEBHOOK)
        val second = paymentReconciler.fetchAndApply(saved, PaymentReconciler.Trigger.WEBHOOK)

        assertThat(first).isEqualTo(PaymentReconciler.Outcome.SUCCEEDED_APPLIED)
        assertThat(second).isEqualTo(PaymentReconciler.Outcome.NOOP_ALREADY_APPLIED)

        val succeededPaymentCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM payments WHERE id = ? AND status = 'succeeded'",
            Int::class.java,
            saved.id
        )
        assertThat(succeededPaymentCount).isEqualTo(1)

        val subscriptionCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM subscriptions WHERE user_id = ?", Int::class.java, userId
        )
        assertThat(subscriptionCount)
            .`as`("idempotent second call must not insert a second subscription row")
            .isEqualTo(1)

        val outboxCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox WHERE event_type = 'subscription.changed' AND aggregate_id = ?",
            Int::class.java,
            userId.toString()
        )
        assertThat(outboxCount)
            .`as`("idempotent second call must not emit a second outbox event")
            .isEqualTo(1)
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

    private fun seedExistingExpiredSubscription(userId: UUID) {
        subscriptionRepository.save(
            Subscription(
                userId = userId,
                planId = "premium_monthly",
                tier = "premium",
                status = "expired",
                startedAt = Instant.now().minusSeconds(86400 * 60L),
                expiresAt = Instant.now().minusSeconds(86400L),
                autoRenew = false
            )
        )
    }

    private fun stubYookassaGet(externalId: String, yookassaStatus: String) {
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
                                "status": "$yookassaStatus",
                                "amount": {"value": "299.00", "currency": "RUB"}
                            }
                            """.trimIndent()
                        )
                )
        )
    }
}
