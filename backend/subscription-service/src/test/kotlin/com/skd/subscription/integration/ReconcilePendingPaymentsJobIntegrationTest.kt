package com.skd.subscription.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.repository.payment.model.Payment
import com.skd.subscription.job.ReconcilePendingPaymentsJob
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.quartz.JobExecutionContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Tag("integration")
class ReconcilePendingPaymentsJobIntegrationTest : IntegrationTestBase() {

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
    lateinit var reconcilePendingPaymentsJob: ReconcilePendingPaymentsJob

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
    fun `job reconciles only pending payments created within the configured window and leaves older payments untouched`() {
        val recentUserId = UUID.randomUUID()
        val oldUserId = UUID.randomUUID()
        val recentExternalId = "yk-recent-reconcile"
        val oldExternalId = "yk-old-reconcile"
        val now = Instant.now()

        // recent pending payment — inside scheduled window (default 60 min)
        seedPendingPayment(recentUserId, recentExternalId, createdAt = now.minus(2, ChronoUnit.MINUTES))
        // old pending payment — outside scheduled window (default 60 min)
        seedPendingPayment(oldUserId, oldExternalId, createdAt = now.minus(90, ChronoUnit.MINUTES))

        stubYookassaGetSucceeded(recentExternalId)
        stubYookassaGetSucceeded(oldExternalId)

        reconcilePendingPaymentsJob.execute(mockJobContext())

        val recentStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM payments WHERE external_id = ?",
            String::class.java,
            recentExternalId
        )
        assertThat(recentStatus)
            .`as`("recent pending payment within the lookback window must be reconciled and marked succeeded")
            .isEqualTo("succeeded")

        val oldStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM payments WHERE external_id = ?",
            String::class.java,
            oldExternalId
        )
        assertThat(oldStatus)
            .`as`("payment older than the configured scheduled window must NOT be touched by the job")
            .isEqualTo("pending")

        val recentSubscriptionCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM subscriptions WHERE user_id = ?",
            Int::class.java,
            recentUserId
        )
        assertThat(recentSubscriptionCount)
            .`as`("reconciliation must create a subscription row for the recent user")
            .isEqualTo(1)

        val oldSubscriptionCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM subscriptions WHERE user_id = ?",
            Int::class.java,
            oldUserId
        )
        assertThat(oldSubscriptionCount)
            .`as`("out-of-window user must NOT receive a subscription from the backstop job")
            .isEqualTo(0)

        val recentOutbox = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox WHERE event_type = 'subscription.changed' AND aggregate_id = ?",
            Int::class.java,
            recentUserId.toString()
        )
        assertThat(recentOutbox)
            .`as`("subscription.changed outbox event must be emitted exactly once for the reconciled user")
            .isEqualTo(1)

        val oldOutbox = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox WHERE event_type = 'subscription.changed' AND aggregate_id = ?",
            Int::class.java,
            oldUserId.toString()
        )
        assertThat(oldOutbox)
            .`as`("no outbox event must be produced for the out-of-window user")
            .isEqualTo(0)
    }

    @Test
    fun `job reconciling a single pending payment with succeeded YooKassa response persists subscription and exactly one outbox row`() {
        val userId = UUID.randomUUID()
        val externalId = "yk-scheduled-succeeded"
        seedPendingPayment(userId, externalId, createdAt = Instant.now().minus(10, ChronoUnit.MINUTES))
        stubYookassaGetSucceeded(externalId)

        reconcilePendingPaymentsJob.execute(mockJobContext())

        val paymentStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM payments WHERE external_id = ?",
            String::class.java,
            externalId
        )
        assertThat(paymentStatus)
            .`as`("pending payment must be marked succeeded after scheduled reconciliation")
            .isEqualTo("succeeded")

        val subscriptionCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM subscriptions WHERE user_id = ? AND status = 'active' AND tier = 'premium'",
            Int::class.java,
            userId
        )
        assertThat(subscriptionCount)
            .`as`("reconciliation must create exactly one active premium subscription for the user")
            .isEqualTo(1)

        val outboxCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox WHERE event_type = 'subscription.changed' AND aggregate_id = ?",
            Int::class.java,
            userId.toString()
        )
        assertThat(outboxCount)
            .`as`("exactly one subscription.changed outbox event must be produced per reconciled payment")
            .isEqualTo(1)
    }

    @Test
    fun `job continues processing remaining payments when YooKassa returns 500 for one payment in the batch`() {
        val failingUserId = UUID.randomUUID()
        val succeedingUserId = UUID.randomUUID()
        val failingExternalId = "yk-batch-fail"
        val succeedingExternalId = "yk-batch-ok"
        val now = Instant.now()

        // Both within the window — older one comes first, newer second (or ordering is implementation detail)
        seedPendingPayment(failingUserId, failingExternalId, createdAt = now.minus(20, ChronoUnit.MINUTES))
        seedPendingPayment(succeedingUserId, succeedingExternalId, createdAt = now.minus(5, ChronoUnit.MINUTES))

        stubYookassaGet500(failingExternalId)
        stubYookassaGetSucceeded(succeedingExternalId)

        // The job MUST NOT propagate per-payment exceptions — execute() should complete normally
        reconcilePendingPaymentsJob.execute(mockJobContext())

        val failingStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM payments WHERE external_id = ?",
            String::class.java,
            failingExternalId
        )
        assertThat(failingStatus)
            .`as`("payment whose YooKassa lookup fails with 500 must remain pending — no partial state applied")
            .isEqualTo("pending")

        val succeedingStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM payments WHERE external_id = ?",
            String::class.java,
            succeedingExternalId
        )
        assertThat(succeedingStatus)
            .`as`("a failure on one payment must NOT prevent reconciliation of subsequent payments in the same run")
            .isEqualTo("succeeded")

        val failingSubscriptionCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM subscriptions WHERE user_id = ?",
            Int::class.java,
            failingUserId
        )
        assertThat(failingSubscriptionCount).isEqualTo(0)

        val succeedingSubscriptionCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM subscriptions WHERE user_id = ?",
            Int::class.java,
            succeedingUserId
        )
        assertThat(succeedingSubscriptionCount)
            .`as`("the succeeding payment in the batch must result in a subscription row being created")
            .isEqualTo(1)

        // Verify the job did attempt both payments (not just the first one before bailing out)
        wireMock.verify(getRequestedFor(urlPathEqualTo("/v3/payments/$failingExternalId")))
        wireMock.verify(getRequestedFor(urlPathEqualTo("/v3/payments/$succeedingExternalId")))
    }

    // --- Helpers ---

    private fun seedPendingPayment(userId: UUID, externalId: String, createdAt: Instant): Payment {
        return paymentRepository.save(
            Payment(
                userId = userId,
                planId = "premium_monthly",
                amountKopecks = 29900,
                status = "pending",
                externalId = externalId,
                idempotencyKey = UUID.randomUUID(),
                createdAt = createdAt,
                updatedAt = createdAt
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

    private fun mockJobContext(): JobExecutionContext = io.mockk.mockk(relaxed = true)
}
