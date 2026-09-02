package com.contentplatform.auth.unit.logging

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.OutputStreamAppender
import com.fasterxml.jackson.databind.ObjectMapper
import net.logstash.logback.encoder.LogstashEncoder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.ByteArrayOutputStream
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent

@Tag("unit")
class JsonLoggingTest {

    private val objectMapper = ObjectMapper()

    @AfterEach
    fun clearMdc() {
        MDC.clear()
    }

    @Test
    fun `logstash encoder emits required canonical JSON fields including MDC`() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val logger = context.getLogger("com.contentplatform.auth.JsonLoggingTestLogger")
            as ch.qos.logback.classic.Logger
        logger.level = Level.TRACE

        val encoder = LogstashEncoder()
        encoder.setCustomFields("""{"service":"auth-service","environment":"test-env"}""")
        encoder.context = context
        encoder.start()

        val baos = ByteArrayOutputStream()
        val appender = OutputStreamAppender<ILoggingEvent>()
        appender.outputStream = baos
        appender.encoder = encoder
        appender.context = context
        appender.start()
        logger.addAppender(appender)

        MDC.put("request_id", "req-unit-abc")
        MDC.put("user_id", "user-unit-xyz")

        try {
            logger.info("unit test structured log message")

            val json = objectMapper.readTree(baos.toByteArray())

            assertThat(json["@timestamp"]).isNotNull()
            assertThat(json["level"]?.asText()).isEqualTo("INFO")
            assertThat(json["logger_name"]?.asText()).isEqualTo("com.contentplatform.auth.JsonLoggingTestLogger")
            assertThat(json["service"]?.asText()).isEqualTo("auth-service")
            assertThat(json["environment"]?.asText()).isEqualTo("test-env")
            assertThat(json["request_id"]?.asText()).isEqualTo("req-unit-abc")
            assertThat(json["user_id"]?.asText()).isEqualTo("user-unit-xyz")
            assertThat(json["message"]?.asText()).isEqualTo("unit test structured log message")
            assertThat(json["thread_name"]).isNotNull()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
            encoder.stop()
        }
    }

    @Test
    fun `logstash encoder omits request_id and user_id when MDC is empty`() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val logger = context.getLogger("com.contentplatform.auth.JsonLoggingNoMdcLogger")
            as ch.qos.logback.classic.Logger
        logger.level = Level.TRACE

        val encoder = LogstashEncoder()
        encoder.setCustomFields("""{"service":"auth-service","environment":"test-env"}""")
        encoder.context = context
        encoder.start()

        val baos = ByteArrayOutputStream()
        val appender = OutputStreamAppender<ILoggingEvent>()
        appender.outputStream = baos
        appender.encoder = encoder
        appender.context = context
        appender.start()
        logger.addAppender(appender)

        try {
            logger.info("message without MDC")

            val json = objectMapper.readTree(baos.toByteArray())

            assertThat(json["request_id"]).isNull()
            assertThat(json["user_id"]).isNull()
            assertThat(json["message"]?.asText()).isEqualTo("message without MDC")
        } finally {
            logger.detachAppender(appender)
            appender.stop()
            encoder.stop()
        }
    }

    @Test
    fun `logstash encoder includes stack_trace field for ERROR level`() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val logger = context.getLogger("com.contentplatform.auth.JsonLoggingErrorLogger")
            as ch.qos.logback.classic.Logger
        logger.level = Level.TRACE

        val encoder = LogstashEncoder()
        encoder.setCustomFields("""{"service":"auth-service","environment":"test-env"}""")
        encoder.context = context
        encoder.start()

        val baos = ByteArrayOutputStream()
        val appender = OutputStreamAppender<ILoggingEvent>()
        appender.outputStream = baos
        appender.encoder = encoder
        appender.context = context
        appender.start()
        logger.addAppender(appender)

        try {
            logger.error("an error occurred", RuntimeException("test exception for stack trace"))

            val json = objectMapper.readTree(baos.toByteArray())

            assertThat(json["level"]?.asText()).isEqualTo("ERROR")
            assertThat(json["stack_trace"]?.asText()).contains("RuntimeException")
            assertThat(json["stack_trace"]?.asText()).contains("test exception for stack trace")
        } finally {
            logger.detachAppender(appender)
            appender.stop()
            encoder.stop()
        }
    }
}
