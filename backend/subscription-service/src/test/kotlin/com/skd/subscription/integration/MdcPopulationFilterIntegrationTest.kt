package com.skd.subscription.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@Tag("integration")
class MdcPopulationFilterIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `filter does not break request chain when X-Request-Id and X-User-Id headers are present`() {
        val headers = HttpHeaders().apply {
            set("X-Request-Id", "test-request-123")
            set("X-User-Id", "test-user-456")
        }
        val entity = HttpEntity<Void>(headers)

        val response = restTemplate.exchange("/health", HttpMethod.GET, entity, Map::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun `filter does not break request chain when MDC headers are absent`() {
        val response = restTemplate.getForEntity("/health", Map::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }
}
