package com.skd.subscription.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.service.SubscriptionService
import com.skd.subscription.integration.rest.yookassa.PaymentProviderUnavailableException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID

@Tag("integration")
class CheckoutTransactionBoundaryIntegrationTest : IntegrationTestBase() {

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
    lateinit var subscriptionService: SubscriptionService

    @Autowired
    lateinit var paymentRepository: PaymentRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun resetWireMock() {
        wireMock.resetAll()
    }

    @Test
    fun `payment row is persisted and marked failed when YooKassa returns 500`() {
        val userId = UUID.randomUUID()
        val planId = "premium_monthly"

        wireMock.stubFor(
            post(urlPathEqualTo("/v3/payments"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"type":"error","code":"internal_server_error","description":"YooKassa unavailable"}""")
                )
        )

        assertThatThrownBy { subscriptionService.checkout(userId, planId) }
            .isInstanceOf(PaymentProviderUnavailableException::class.java)

        val rowCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM payments WHERE user_id = ? AND plan_id = ?",
            Int::class.java,
            userId,
            planId
        )
        assertThat(rowCount)
            .`as`("payment row must survive failure of the external YooKassa call")
            .isEqualTo(1)

        val status = jdbcTemplate.queryForObject(
            "SELECT status FROM payments WHERE user_id = ? AND plan_id = ?",
            String::class.java,
            userId,
            planId
        )
        assertThat(status)
            .`as`("payment must be marked failed so next checkout is not short-circuited by pending")
            .isEqualTo("failed")
    }

    @Test
    fun `payment row is marked failed with no external fields when YooKassa returns 503`() {
        val userId = UUID.randomUUID()
        val planId = "premium_monthly"

        wireMock.stubFor(
            post(urlPathEqualTo("/v3/payments"))
                .willReturn(
                    aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"type":"error","code":"service_unavailable","description":"Service is unavailable"}""")
                )
        )

        assertThatThrownBy { subscriptionService.checkout(userId, planId) }
            .isInstanceOf(PaymentProviderUnavailableException::class.java)

        val externalId = jdbcTemplate.queryForObject(
            "SELECT external_id FROM payments WHERE user_id = ? AND plan_id = ?",
            String::class.java,
            userId,
            planId
        )
        val confirmationUrl = jdbcTemplate.queryForObject(
            "SELECT confirmation_url FROM payments WHERE user_id = ? AND plan_id = ?",
            String::class.java,
            userId,
            planId
        )
        val status = jdbcTemplate.queryForObject(
            "SELECT status FROM payments WHERE user_id = ? AND plan_id = ?",
            String::class.java,
            userId,
            planId
        )

        assertThat(status).isEqualTo("failed")
        assertThat(externalId).isNull()
        assertThat(confirmationUrl).isNull()
    }

    @Test
    fun `second checkout is not short-circuited after previous YooKassa failure`() {
        val userId = UUID.randomUUID()
        val planId = "premium_monthly"

        // First attempt: YooKassa fails
        wireMock.stubFor(
            post(urlPathEqualTo("/v3/payments"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"type":"error","code":"internal_server_error"}""")
                )
        )

        assertThatThrownBy { subscriptionService.checkout(userId, planId) }
            .isInstanceOf(PaymentProviderUnavailableException::class.java)

        // Second attempt: YooKassa succeeds
        wireMock.resetAll()
        wireMock.stubFor(
            post(urlPathEqualTo("/v3/payments"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "id": "yookassa-retry-001",
                                "status": "pending",
                                "amount": {"value": "299.00", "currency": "RUB"},
                                "confirmation": {"type": "redirect", "confirmation_url": "https://yookassa.ru/pay/retry-001"}
                            }
                            """.trimIndent()
                        )
                )
        )

        val result = subscriptionService.checkout(userId, planId)

        assertThat(result.confirmationUrl).isEqualTo("https://yookassa.ru/pay/retry-001")

        val totalRows = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM payments WHERE user_id = ? AND plan_id = ?",
            Int::class.java,
            userId,
            planId
        )
        assertThat(totalRows)
            .`as`("should have one failed and one successful payment row")
            .isEqualTo(2)

        val failedCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM payments WHERE user_id = ? AND plan_id = ? AND status = 'failed'",
            Int::class.java,
            userId,
            planId
        )
        assertThat(failedCount).isEqualTo(1)
    }

    @Test
    fun `successful checkout persists payment with external fields populated`() {
        val userId = UUID.randomUUID()
        val planId = "premium_monthly"

        wireMock.stubFor(
            post(urlPathEqualTo("/v3/payments"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "id": "yookassa-success-001",
                                "status": "pending",
                                "amount": {"value": "299.00", "currency": "RUB"},
                                "confirmation": {"type": "redirect", "confirmation_url": "https://yookassa.ru/pay/success-001"}
                            }
                            """.trimIndent()
                        )
                )
        )

        val result = subscriptionService.checkout(userId, planId)

        assertThat(result.confirmationUrl).isEqualTo("https://yookassa.ru/pay/success-001")

        val externalId = jdbcTemplate.queryForObject(
            "SELECT external_id FROM payments WHERE user_id = ? AND plan_id = ?",
            String::class.java,
            userId,
            planId
        )
        val confirmationUrl = jdbcTemplate.queryForObject(
            "SELECT confirmation_url FROM payments WHERE user_id = ? AND plan_id = ?",
            String::class.java,
            userId,
            planId
        )

        assertThat(externalId).isEqualTo("yookassa-success-001")
        assertThat(confirmationUrl).isEqualTo("https://yookassa.ru/pay/success-001")
    }
}
