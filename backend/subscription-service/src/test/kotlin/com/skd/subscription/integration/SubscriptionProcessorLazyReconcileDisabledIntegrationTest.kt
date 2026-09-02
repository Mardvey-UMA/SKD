package com.skd.subscription.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
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
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.util.UUID

@Tag("integration")
@TestPropertySource(
    properties = [
        "subscription.reconcile.lazy.enabled=false"
    ]
)
class SubscriptionProcessorLazyReconcileDisabledIntegrationTest : IntegrationTestBase() {

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
    fun `getStatus with lazy disabled makes zero YooKassa calls even when pending payment exists`() {
        val userId = UUID.randomUUID()
        val externalId = "yk-lazy-disabled-1"
        seedPendingPayment(userId, externalId)

        val result = subscriptionProcessor.getStatus(userId)

        assertThat(result.tier)
            .`as`("with lazy disabled, status reflects only persisted subscription (none → free)")
            .isEqualTo("free")

        wireMock.verify(
            0,
            getRequestedFor(urlMatching("/v3/payments/.*"))
        )

        val paymentStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM payments WHERE external_id = ?",
            String::class.java,
            externalId
        )
        assertThat(paymentStatus)
            .`as`("payment must remain pending — no reconciliation performed")
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
}
