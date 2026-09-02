package com.contentplatform.user.unit.logging

import net.logstash.logback.encoder.LogstashEncoder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class StructuredLoggingTest {

    @Test
    fun `logstash encoder can be instantiated`() {
        val encoder = LogstashEncoder()
        // Test that the encoder can be instantiated without error
        assertThat(encoder).isNotNull()
    }

    @Test
    fun `logback-spring xml is present on classpath`() {
        val resource = javaClass.classLoader.getResourceAsStream("logback-spring.xml")
        assertThat(resource).isNotNull()
        resource!!.close()
    }

    @Test
    fun `logback-spring xml contains required fields configuration`() {
        val resource = javaClass.classLoader.getResourceAsStream("logback-spring.xml")!!
        val content = resource.bufferedReader().readText()
        resource.close()

        assertThat(content).contains("LogstashEncoder")
        assertThat(content).contains("@timestamp")
        assertThat(content).contains("logger_name")
        assertThat(content).contains("thread_name")
        assertThat(content).contains("stack_trace")
        assertThat(content).contains("user-service")
        assertThat(content).contains("request_id")
        assertThat(content).contains("user_id")
    }
}
