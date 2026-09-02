package com.skd.subscription.integration.rest.yookassa

import com.skd.subscription.integration.rest.yookassa.model.YookassaCreatePaymentRequest
import com.skd.subscription.integration.rest.yookassa.model.YookassaPaymentResponse

interface YookassaClient {

    fun createPayment(request: YookassaCreatePaymentRequest): YookassaPaymentResponse

    fun createAutopayment(request: YookassaCreatePaymentRequest): YookassaPaymentResponse

    fun getPayment(paymentId: String): YookassaPaymentResponse

    fun capturePayment(paymentId: String, amount: String, idempotencyKey: String): YookassaPaymentResponse
}
