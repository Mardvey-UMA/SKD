package com.skd.subscription.integration.rest.yookassa.model

data class YookassaPaymentResponse(
    val id: String,
    val status: String,
    val confirmationUrl: String?,
    val amount: Amount,
    val paymentMethodId: String? = null,
    val paymentMethodType: String? = null,
    val cardLast4: String? = null
) {
    data class Amount(
        val value: String,
        val currency: String
    )
}
