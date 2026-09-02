package com.skd.userinteractions.unit

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import java.io.InputStream

@Tag("unit")
class LogbackConfigurationTest {

    @Test
    fun `logback-spring xml exists and contains required fields`() {
        val stream: InputStream? = javaClass.classLoader.getResourceAsStream("logback-spring.xml")
        assertThat(stream).isNotNull
        val content = stream!!.bufferedReader().readText()
        assertThat(content).contains("LogstashEncoder")
        assertThat(content).contains("@timestamp")
        assertThat(content).contains("logger_name")
        assertThat(content).contains("thread_name")
        assertThat(content).contains("stack_trace")
        assertThat(content).contains("user-interactions-service")
        assertThat(content).contains("request_id")
        assertThat(content).contains("user_id")
    }
}
