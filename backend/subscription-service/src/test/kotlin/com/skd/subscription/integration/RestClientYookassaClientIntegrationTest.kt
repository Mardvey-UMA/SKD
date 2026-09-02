package com.skd.subscription.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.skd.subscription.integration.rest.yookassa.RestClientYookassaClient
import com.skd.subscription.integration.rest.yookassa.YookassaClient
import com.skd.subscription.integration.rest.yookassa.model.YookassaCreatePaymentRequest
import com.skd.subscription.integration.rest.yookassa.model.YookassaPaymentResponse
import com.skd.subscription.configuration.YookassaProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.web.client.HttpClientErrorException
import java.util.Base64

@Tag("integration")
class RestClientYookassaClientIntegrationTest {

    companion object {
        private lateinit var wireMock: WireMockServer
        private lateinit var client: YookassaClient
        private const val SHOP_ID = "test-shop-123"
        private const val SECRET_KEY = "test-secret-abc"

        @BeforeAll
        @JvmStatic
        fun startWireMock() {
            wireMock = WireMockServer(wireMockConfig().dynamicPort())
            wireMock.start()
            val properties = YookassaProperties(
                shopId = SHOP_ID,
                secretKey = SECRET_KEY,
                apiUrl = "http://localhost:${wireMock.port()}/v3",
                webhook = YookassaProperties.Webhook(
                    allowedCidrs = "185.71.76.0/27"
                )
            )
            client = RestClientYookassaClient(properties)
        }

        @AfterAll
        @JvmStatic
        fun stopWireMock() {
            wireMock.stop()
        }
    }

    @BeforeEach
    fun resetWireMock() {
        wireMock.resetAll()
    }

    private fun expectedBasicAuth(): String {
        val credentials = "$SHOP_ID:$SECRET_KEY"
        return "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())
    }

    @Nested
    inner class CreatePayment {

        @Test
        fun `should send POST to payments with correct JSON body and return parsed response`() {
            wireMock.stubFor(
                post(urlPathEqualTo("/v3/payments"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                                {
                                    "id": "pay-001",
                                    "status": "pending",
                                    "amount": {"value": "299.00", "currency": "RUB"},
                                    "confirmation": {"type": "redirect", "confirmation_url": "https://yookassa.ru/pay/123"},
                                    "payment_method": {"id": "pm-001", "type": "bank_card", "card": {"last4": "4242"}}
                                }
                                """.trimIndent()
                            )
                    )
            )

            val request = YookassaCreatePaymentRequest(
                amount = YookassaCreatePaymentRequest.Amount(value = "299.00", currency = "RUB"),
                confirmation = YookassaCreatePaymentRequest.Confirmation(type = "redirect", returnUrl = "https://example.com/return"),
                description = "Premium monthly subscription",
                capture = true,
                idempotencyKey = "idem-key-001"
            )

            val response = client.createPayment(request)

            assertThat(response.id).isEqualTo("pay-001")
            assertThat(response.status).isEqualTo("pending")
            assertThat(response.confirmationUrl).isEqualTo("https://yookassa.ru/pay/123")
            assertThat(response.amount.value).isEqualTo("299.00")
            assertThat(response.amount.currency).isEqualTo("RUB")
            assertThat(response.paymentMethodId).isEqualTo("pm-001")
            assertThat(response.paymentMethodType).isEqualTo("bank_card")
            assertThat(response.cardLast4).isEqualTo("4242")
        }

        @Test
        fun `should include Basic Auth header with shopId and secretKey`() {
            wireMock.stubFor(
                post(urlPathEqualTo("/v3/payments"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"id":"p1","status":"pending","amount":{"value":"100.00","currency":"RUB"}}""")
                    )
            )

            val request = YookassaCreatePaymentRequest(
                amount = YookassaCreatePaymentRequest.Amount(value = "100.00"),
                confirmation = null,
                description = "test",
                idempotencyKey = "key-auth-test"
            )

            client.createPayment(request)

            wireMock.verify(
                postRequestedFor(urlPathEqualTo("/v3/payments"))
                    .withHeader("Authorization", equalTo(expectedBasicAuth()))
            )
        }

        @Test
        fun `should include Idempotence-Key header from request`() {
            wireMock.stubFor(
                post(urlPathEqualTo("/v3/payments"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"id":"p2","status":"pending","amount":{"value":"100.00","currency":"RUB"}}""")
                    )
            )

            val idempotencyKey = "unique-idem-key-12345"
            val request = YookassaCreatePaymentRequest(
                amount = YookassaCreatePaymentRequest.Amount(value = "100.00"),
                confirmation = null,
                description = "test",
                idempotencyKey = idempotencyKey
            )

            client.createPayment(request)

            wireMock.verify(
                postRequestedFor(urlPathEqualTo("/v3/payments"))
                    .withHeader("Idempotence-Key", equalTo(idempotencyKey))
            )
        }

        @Test
        fun `should include confirmation block in body when confirmation is provided`() {
            wireMock.stubFor(
                post(urlPathEqualTo("/v3/payments"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"id":"p3","status":"pending","amount":{"value":"299.00","currency":"RUB"},"confirmation":{"confirmation_url":"https://pay.test"}}""")
                    )
            )

            val request = YookassaCreatePaymentRequest(
                amount = YookassaCreatePaymentRequest.Amount(value = "299.00"),
                confirmation = YookassaCreatePaymentRequest.Confirmation(
                    type = "redirect",
                    returnUrl = "https://example.com/return"
                ),
                description = "with confirmation",
                idempotencyKey = "key-with-confirm"
            )

            client.createPayment(request)

            wireMock.verify(
                postRequestedFor(urlPathEqualTo("/v3/payments"))
                    .withRequestBody(
                        matching(""".*"confirmation".*"type".*"redirect".*"return_url".*"https://example.com/return".*""")
                    )
            )
        }

        @Test
        fun `should throw exception on 400 Bad Request`() {
            wireMock.stubFor(
                post(urlPathEqualTo("/v3/payments"))
                    .willReturn(
                        aResponse()
                            .withStatus(400)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"type":"error","code":"invalid_request","description":"Invalid amount"}""")
                    )
            )

            val request = YookassaCreatePaymentRequest(
                amount = YookassaCreatePaymentRequest.Amount(value = "-1.00"),
                confirmation = null,
                description = "bad request",
                idempotencyKey = "key-bad-request"
            )

            assertThatThrownBy { client.createPayment(request) }
                .isInstanceOf(HttpClientErrorException::class.java)
        }

        @Test
        fun `should throw exception on 422 Unprocessable Entity`() {
            wireMock.stubFor(
                post(urlPathEqualTo("/v3/payments"))
                    .willReturn(
                        aResponse()
                            .withStatus(422)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"type":"error","code":"unprocessable_entity","description":"Duplicate idempotency key"}""")
                    )
            )

            val request = YookassaCreatePaymentRequest(
                amount = YookassaCreatePaymentRequest.Amount(value = "100.00"),
                confirmation = null,
                description = "duplicate",
                idempotencyKey = "key-duplicate"
            )

            assertThatThrownBy { client.createPayment(request) }
                .isInstanceOf(HttpClientErrorException::class.java)
        }
    }

    @Nested
    inner class CreateAutopayment {

        @Test
        fun `should send payment_method_id and no confirmation block for autopayment`() {
            wireMock.stubFor(
                post(urlPathEqualTo("/v3/payments"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                                {
                                    "id": "auto-pay-001",
                                    "status": "succeeded",
                                    "amount": {"value": "299.00", "currency": "RUB"},
                                    "payment_method": {"id": "pm-saved-001", "type": "bank_card", "card": {"last4": "1234"}}
                                }
                                """.trimIndent()
                            )
                    )
            )

            val request = YookassaCreatePaymentRequest(
                amount = YookassaCreatePaymentRequest.Amount(value = "299.00"),
                confirmation = null,
                description = "Auto-renewal premium monthly",
                capture = true,
                idempotencyKey = "auto-idem-key-001",
                paymentMethodId = "pm-saved-001"
            )

            val response = client.createPayment(request)

            assertThat(response.id).isEqualTo("auto-pay-001")
            assertThat(response.status).isEqualTo("succeeded")
            assertThat(response.confirmationUrl).isNull()
            assertThat(response.paymentMethodId).isEqualTo("pm-saved-001")

            wireMock.verify(
                postRequestedFor(urlPathEqualTo("/v3/payments"))
                    .withRequestBody(matching(""".*"payment_method_id".*"pm-saved-001".*"""))
                    .withoutHeader("confirmation")
            )
        }

        @Test
        fun `should not include confirmation key in body when confirmation is null`() {
            wireMock.stubFor(
                post(urlPathEqualTo("/v3/payments"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"id":"auto-p2","status":"succeeded","amount":{"value":"100.00","currency":"RUB"}}""")
                    )
            )

            val request = YookassaCreatePaymentRequest(
                amount = YookassaCreatePaymentRequest.Amount(value = "100.00"),
                confirmation = null,
                description = "autopay",
                idempotencyKey = "auto-key-no-confirm",
                paymentMethodId = "pm-002"
            )

            client.createPayment(request)

            wireMock.verify(
                postRequestedFor(urlPathEqualTo("/v3/payments"))
                    .withRequestBody(matching("^(?!.*\"confirmation\").*$"))
            )
        }
    }

    @Nested
    inner class GetPayment {

        @Test
        fun `should send GET request and return payment data`() {
            val paymentId = "pay-get-001"
            wireMock.stubFor(
                get(urlEqualTo("/v3/payments/$paymentId"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                                {
                                    "id": "$paymentId",
                                    "status": "succeeded",
                                    "amount": {"value": "299.00", "currency": "RUB"},
                                    "payment_method": {"id": "pm-get-001", "type": "bank_card", "card": {"last4": "9999"}}
                                }
                                """.trimIndent()
                            )
                    )
            )

            val response = client.getPayment(paymentId)

            assertThat(response.id).isEqualTo(paymentId)
            assertThat(response.status).isEqualTo("succeeded")
            assertThat(response.amount.value).isEqualTo("299.00")
            assertThat(response.paymentMethodId).isEqualTo("pm-get-001")
            assertThat(response.cardLast4).isEqualTo("9999")
        }

        @Test
        fun `should include Basic Auth header on GET request`() {
            val paymentId = "pay-auth-check"
            wireMock.stubFor(
                get(urlEqualTo("/v3/payments/$paymentId"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"id":"$paymentId","status":"pending","amount":{"value":"100.00","currency":"RUB"}}""")
                    )
            )

            client.getPayment(paymentId)

            wireMock.verify(
                getRequestedFor(urlEqualTo("/v3/payments/$paymentId"))
                    .withHeader("Authorization", equalTo(expectedBasicAuth()))
            )
        }

        @Test
        fun `should handle payment without payment_method`() {
            val paymentId = "pay-no-method"
            wireMock.stubFor(
                get(urlEqualTo("/v3/payments/$paymentId"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"id":"$paymentId","status":"canceled","amount":{"value":"50.00","currency":"RUB"}}""")
                    )
            )

            val response = client.getPayment(paymentId)

            assertThat(response.id).isEqualTo(paymentId)
            assertThat(response.paymentMethodId).isNull()
            assertThat(response.paymentMethodType).isNull()
            assertThat(response.cardLast4).isNull()
        }
    }

    @Nested
    inner class CapturePayment {

        @Test
        fun `should send POST to payments capture endpoint with amount body`() {
            val paymentId = "pay-capture-001"
            wireMock.stubFor(
                post(urlEqualTo("/v3/payments/$paymentId/capture"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                                {
                                    "id": "$paymentId",
                                    "status": "succeeded",
                                    "amount": {"value": "299.00", "currency": "RUB"}
                                }
                                """.trimIndent()
                            )
                    )
            )

            val response = client.capturePayment(paymentId, "299.00", "capture-idem-001")

            assertThat(response.id).isEqualTo(paymentId)
            assertThat(response.status).isEqualTo("succeeded")
        }

        @Test
        fun `should include Idempotence-Key header on capture request`() {
            val paymentId = "pay-capture-idem"
            val idempotencyKey = "capture-idem-key-unique"
            wireMock.stubFor(
                post(urlEqualTo("/v3/payments/$paymentId/capture"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"id":"$paymentId","status":"succeeded","amount":{"value":"100.00","currency":"RUB"}}""")
                    )
            )

            client.capturePayment(paymentId, "100.00", idempotencyKey)

            wireMock.verify(
                postRequestedFor(urlEqualTo("/v3/payments/$paymentId/capture"))
                    .withHeader("Idempotence-Key", equalTo(idempotencyKey))
                    .withHeader("Authorization", equalTo(expectedBasicAuth()))
            )
        }

        @Test
        fun `should send amount in request body for capture`() {
            val paymentId = "pay-capture-body"
            wireMock.stubFor(
                post(urlEqualTo("/v3/payments/$paymentId/capture"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"id":"$paymentId","status":"succeeded","amount":{"value":"599.00","currency":"RUB"}}""")
                    )
            )

            client.capturePayment(paymentId, "599.00", "capture-body-key")

            wireMock.verify(
                postRequestedFor(urlEqualTo("/v3/payments/$paymentId/capture"))
                    .withRequestBody(equalToJson("""{"amount":{"value":"599.00","currency":"RUB"}}"""))
            )
        }
    }

    @Nested
    inner class Timeouts {

        @Test
        fun `should fail fast with ResourceAccessException when YooKassa response exceeds read timeout`() {
            val readTimeoutMs = 2000
            val serverDelayMs = readTimeoutMs + 5000
            val toleranceMs = readTimeoutMs + 5000L

            wireMock.stubFor(
                post(urlPathEqualTo("/v3/payments"))
                    .willReturn(
                        aResponse()
                            .withFixedDelay(serverDelayMs)
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"id":"slow","status":"pending","amount":{"value":"100.00","currency":"RUB"}}""")
                    )
            )

            val timeoutClient = RestClientYookassaClient(
                YookassaProperties(
                    shopId = SHOP_ID,
                    secretKey = SECRET_KEY,
                    apiUrl = "http://localhost:${wireMock.port()}/v3",
                    connectTimeoutMs = 1000,
                    readTimeoutMs = readTimeoutMs
                )
            )

            val request = YookassaCreatePaymentRequest(
                amount = YookassaCreatePaymentRequest.Amount(value = "100.00"),
                confirmation = null,
                description = "slow stub",
                idempotencyKey = "timeout-test-key"
            )

            val startNanos = System.nanoTime()
            assertThatThrownBy { timeoutClient.createPayment(request) }
                .isInstanceOf(org.springframework.web.client.ResourceAccessException::class.java)
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000

            assertThat(elapsedMs)
                .`as`("createPayment must abort well before the server-side delay of %d ms", serverDelayMs)
                .isLessThan(toleranceMs)
        }
    }
}
