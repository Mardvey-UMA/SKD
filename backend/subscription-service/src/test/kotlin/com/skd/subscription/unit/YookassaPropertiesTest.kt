package com.skd.subscription.unit

import com.skd.subscription.configuration.YookassaProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class YookassaPropertiesTest {

    @Test
    fun `should hold shopId, secretKey, apiUrl and webhook properties`() {
        val properties = YookassaProperties(
            shopId = "shop-123",
            secretKey = "secret-abc",
            apiUrl = "https://api.yookassa.ru/v3",
            webhook = YookassaProperties.Webhook(
                allowedCidrs = "185.71.76.0/27,185.71.77.0/27"
            )
        )

        assertThat(properties.shopId).isEqualTo("shop-123")
        assertThat(properties.secretKey).isEqualTo("secret-abc")
        assertThat(properties.apiUrl).isEqualTo("https://api.yookassa.ru/v3")
        assertThat(properties.webhook.allowedCidrs).isEqualTo("185.71.76.0/27,185.71.77.0/27")
    }

    @Test
    fun `should be a data class with copy support`() {
        val original = YookassaProperties(
            shopId = "shop-1",
            secretKey = "secret-1",
            apiUrl = "https://api.yookassa.ru/v3",
            webhook = YookassaProperties.Webhook(allowedCidrs = "0.0.0.0/0")
        )

        val modified = original.copy(shopId = "shop-2")

        assertThat(modified.shopId).isEqualTo("shop-2")
        assertThat(modified.secretKey).isEqualTo("secret-1")
    }

    @Test
    fun `webhook should parse comma-separated CIDRs as a string`() {
        val webhook = YookassaProperties.Webhook(
            allowedCidrs = "185.71.76.0/27,185.71.77.0/27,77.75.153.0/25"
        )

        assertThat(webhook.allowedCidrs).contains("185.71.76.0/27")
        assertThat(webhook.allowedCidrs.split(",")).hasSize(3)
    }

    @Test
    fun `should default connectTimeoutMs to 5000`() {
        val properties = YookassaProperties()

        assertThat(properties.connectTimeoutMs).isEqualTo(5000)
    }

    @Test
    fun `should default readTimeoutMs to 20000`() {
        val properties = YookassaProperties()

        assertThat(properties.readTimeoutMs).isEqualTo(20000)
    }

    @Test
    fun `should allow overriding connectTimeoutMs and readTimeoutMs`() {
        val properties = YookassaProperties(
            connectTimeoutMs = 1234,
            readTimeoutMs = 9999
        )

        assertThat(properties.connectTimeoutMs).isEqualTo(1234)
        assertThat(properties.readTimeoutMs).isEqualTo(9999)
    }

    @Test
    fun `should default returnUrl to empty string`() {
        val properties = YookassaProperties()

        assertThat(properties.returnUrl).isEqualTo("")
    }

    @Test
    fun `should allow overriding returnUrl to a custom value`() {
        val properties = YookassaProperties(
            returnUrl = "https://my-app.com/return"
        )

        assertThat(properties.returnUrl).isEqualTo("https://my-app.com/return")
    }
}
