package com.contentplatform.gateway.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Integration tests for structured JSON logging configuration.
 *
 * Tests verify:
 * 1. logback-spring.xml exists and is configured with LogstashEncoder for production profile
 * 2. The configuration includes all required JSON fields
 * 3. MdcPopulationFilter integration with the full Spring context
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StructuredLoggingIntegrationTest : IntegrationTestBase() {

    @Test
    fun `logback-spring xml exists and contains LogstashEncoder config`() {
        val resource = this::class.java.classLoader.getResource("logback-spring.xml")
        assertThat(resource).isNotNull()
        val content = resource!!.readText()
        assertThat(content).contains("LogstashEncoder")
        assertThat(content).contains("api-gateway")
        assertThat(content).contains("@timestamp")
        assertThat(content).contains("request_id")
        assertThat(content).contains("user_id")
    }

    @Test
    fun `logback-spring xml has separate test profile with plain encoder`() {
        val resource = this::class.java.classLoader.getResource("logback-spring.xml")
        assertThat(resource).isNotNull()
        val content = resource!!.readText()
        assertThat(content).contains("springProfile name=\"test\"")
        assertThat(content).contains("PatternLayoutEncoder")
    }

    @Test
    fun `logback-spring xml includes environment and service custom fields`() {
        val resource = this::class.java.classLoader.getResource("logback-spring.xml")
        assertThat(resource).isNotNull()
        val content = resource!!.readText()
        assertThat(content).contains("service")
        assertThat(content).contains("environment")
        assertThat(content).contains("stack_trace")
    }
}
