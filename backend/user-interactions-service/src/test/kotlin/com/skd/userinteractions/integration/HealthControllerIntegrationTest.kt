package com.skd.userinteractions.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthControllerIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("interactions_db")
            .withUsername("test")
            .withPassword("test")

        @Container
        @JvmStatic
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
        }
    }

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `GET health returns 200`() {
        val response = restTemplate.getForEntity("/health", Map::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun `health response contains status ok`() {
        val response = restTemplate.getForEntity("/health", Map::class.java)
        val body = response.body!!

        assertThat(body["status"]).isEqualTo("ok")
    }

    @Test
    fun `health response contains service name`() {
        val response = restTemplate.getForEntity("/health", Map::class.java)
        val body = response.body!!

        assertThat(body["service"]).isEqualTo("user-interactions-service")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `health response contains database check`() {
        val response = restTemplate.getForEntity("/health", Map::class.java)
        val body = response.body!!
        val checks = body["checks"] as Map<String, Any>

        assertThat(checks["database"]).isEqualTo("connected")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `health response contains kafka check`() {
        val response = restTemplate.getForEntity("/health", Map::class.java)
        val body = response.body!!
        val checks = body["checks"] as Map<String, Any>

        assertThat(checks["kafka"]).isEqualTo("connected")
    }

    @Test
    fun `health endpoint does not require HMAC authentication`() {
        // No X-Gateway-Signature header, should still return 200
        val response = restTemplate.getForEntity("/health", Map::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }
}
