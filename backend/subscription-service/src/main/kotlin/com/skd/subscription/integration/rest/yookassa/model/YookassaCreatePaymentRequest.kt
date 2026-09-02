package com.skd.subscription.integration.rest.yookassa.model

data class YookassaCreatePaymentRequest(
    val amount: Amount,
    val confirmation: Confirmation?,
    val description: String?,
    val capture: Boolean = true,
    val idempotencyKey: String,
    val paymentMethodId: String? = null
) {
    data class Amount(
        val value: String,
        val currency: String = "RUB"
    )

    data class Confirmation(
        val type: String = "redirect",
        val returnUrl: String
    )
}
