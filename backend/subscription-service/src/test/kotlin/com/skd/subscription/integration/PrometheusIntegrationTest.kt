package com.skd.subscription.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus

@Tag("integration")
class PrometheusIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `GET actuator prometheus returns 200 with subscription_checkout_count_total metric`() {
        val response = restTemplate.getForEntity("/actuator/prometheus", String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("subscription_checkout_count_total")
    }

    @Test
    fun `GET actuator prometheus includes yookassa_webhook_count_total metric`() {
        val response = restTemplate.getForEntity("/actuator/prometheus", String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("yookassa_webhook_count_total")
    }

    @Test
    fun `GET actuator prometheus includes subscription_reconcile_count_total metric`() {
        val response = restTemplate.getForEntity("/actuator/prometheus", String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("subscription_reconcile_count_total")
    }
}
