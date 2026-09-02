package com.skd.subscription.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "yookassa")
data class YookassaProperties(
    var shopId: String = "",
    var secretKey: String = "",
    var apiUrl: String = "",
    var webhook: Webhook = Webhook(),
    var connectTimeoutMs: Int = 5000,
    var readTimeoutMs: Int = 20000,
    var returnUrl: String = ""
) {
    data class Webhook(
        var allowedCidrs: String = "",
        var devMode: Boolean = false
    )
}
