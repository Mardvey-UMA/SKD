package com.contentplatform.user.integration

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat

@Tag("integration")
class ApplicationContextIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    private val objectMapper = ObjectMapper()

    @Test
    fun `application context loads successfully`() {
        // If context fails to load, this test fails before reaching any assertion.
        // Reaching here means Spring Boot started with PostgreSQL + Kafka containers.
        assertThat(true).isTrue()
    }

    @Test
    fun `health endpoint returns 200 with correct structure`() {
        val response = restTemplate.getForEntity("/health", String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

        val body = objectMapper.readTree(response.body)
        assertThat(body.get("status").asText()).isEqualTo("ok")
        assertThat(body.get("service").asText()).isEqualTo("user-service")
        assertThat(body.has("checks")).isTrue()
    }

    @Test
    fun `application listens on configured port`() {
        val response = restTemplate.getForEntity("/health", String::class.java)

        // If the app is not listening, this call would throw a connection error.
        // A non-null response (any status) proves the server is up.
        assertThat(response).isNotNull()
    }
}
