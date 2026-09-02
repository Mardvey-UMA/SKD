package com.skd.subscription.integration.rest.yookassa

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.skd.subscription.configuration.YookassaProperties
import com.skd.subscription.integration.rest.yookassa.model.YookassaCreatePaymentRequest
import com.skd.subscription.integration.rest.yookassa.model.YookassaPaymentResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration
import java.util.Base64

@Component
class RestClientYookassaClient(
    private val properties: YookassaProperties
) : YookassaClient {

    private val shopId: String get() = properties.shopId
    private val secretKey: String get() = properties.secretKey
    private val apiUrl: String get() = properties.apiUrl

    companion object {
        private val log = LoggerFactory.getLogger(RestClientYookassaClient::class.java)
    }

    private val restClient: RestClient by lazy {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs.toLong()))
            .version(HttpClient.Version.HTTP_1_1)
            .build()
        val factory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofMillis(properties.readTimeoutMs.toLong()))
        }
        RestClient.builder()
            .baseUrl(apiUrl)
            .requestFactory(factory)
            .defaultHeader("Authorization", basicAuth())
            .build()
    }

    override fun createPayment(request: YookassaCreatePaymentRequest): YookassaPaymentResponse {
        val body = buildCreatePaymentBody(request)
        val response = restClient.post()
            .uri("/payments")
            .header("Idempotence-Key", request.idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(YookassaApiPaymentResponse::class.java)!!
        return response.toDomain()
    }

    override fun getPayment(paymentId: String): YookassaPaymentResponse {
        val response = restClient.get()
            .uri("/payments/$paymentId")
            .retrieve()
            .body(YookassaApiPaymentResponse::class.java)!!
        return response.toDomain()
    }

    override fun createAutopayment(request: YookassaCreatePaymentRequest): YookassaPaymentResponse {
        val body = buildCreatePaymentBody(request)
        val response = restClient.post()
            .uri("/payments")
            .header("Idempotence-Key", request.idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(YookassaApiPaymentResponse::class.java)!!
        return response.toDomain()
    }

    override fun capturePayment(paymentId: String, amount: String, idempotencyKey: String): YookassaPaymentResponse {
        val body = mapOf("amount" to mapOf("value" to amount, "currency" to "RUB"))
        val response = restClient.post()
            .uri("/payments/$paymentId/capture")
            .header("Idempotence-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(YookassaApiPaymentResponse::class.java)!!
        return response.toDomain()
    }

    private fun basicAuth(): String {
        val credentials = "$shopId:$secretKey"
        return "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())
    }

    private fun buildCreatePaymentBody(request: YookassaCreatePaymentRequest): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "amount" to mapOf("value" to request.amount.value, "currency" to request.amount.currency),
            "capture" to request.capture,
            "description" to request.description
        )
        request.confirmation?.let {
            map["confirmation"] = mapOf("type" to it.type, "return_url" to it.returnUrl)
        }
        request.paymentMethodId?.let {
            map["payment_method_id"] = it
        }
        return map
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class YookassaApiPaymentResponse(
        val id: String = "",
        val status: String = "",
        val amount: AmountApi = AmountApi(),
        val confirmation: ConfirmationApi? = null,
        @JsonProperty("payment_method")
        val paymentMethod: PaymentMethodApi? = null
    ) {
        fun toDomain() = YookassaPaymentResponse(
            id = id,
            status = status,
            confirmationUrl = confirmation?.confirmationUrl,
            amount = YookassaPaymentResponse.Amount(value = amount.value, currency = amount.currency),
            paymentMethodId = paymentMethod?.id,
            paymentMethodType = paymentMethod?.type,
            cardLast4 = paymentMethod?.card?.last4
        )
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class AmountApi(
        val value: String = "",
        val currency: String = "RUB"
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class ConfirmationApi(
        @JsonProperty("confirmation_url")
        val confirmationUrl: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class PaymentMethodApi(
        val id: String? = null,
        val type: String? = null,
        val card: CardApi? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class CardApi(
        @JsonProperty("last4")
        val last4: String? = null
    )
}
