package com.contentplatform.auth.api.controller

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

data class HealthResponse(
    val status: String,
    val service: String,
    val checks: Map<String, String>
)

@RestController
class HealthController(
    private val dataSource: DataSource,
    private val redisTemplate: StringRedisTemplate,
    private val kafkaAdmin: KafkaAdmin
) {

    companion object {
        private val log = LoggerFactory.getLogger(HealthController::class.java)
    }

    @GetMapping("/health")
    fun health(): HealthResponse {
        val checks = mapOf(
            "database" to checkDatabase(),
            "redis" to checkRedis(),
            "kafka" to checkKafka()
        )
        val status = if (checks.values.all { it == "connected" }) "ok" else "degraded"
        return HealthResponse(status = status, service = "auth-service", checks = checks)
    }

    private fun checkDatabase(): String {
        return try {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT 1").use { it.execute() }
            }
            "connected"
        } catch (e: Exception) {
            log.warn("Database health check failed", e)
            "disconnected"
        }
    }

    private fun checkRedis(): String {
        return try {
            redisTemplate.connectionFactory?.connection?.ping()
            "connected"
        } catch (e: Exception) {
            log.warn("Redis health check failed", e)
            "disconnected"
        }
    }

    private fun checkKafka(): String {
        return try {
            kafkaAdmin.describeTopics("__consumer_offsets")
            "connected"
        } catch (e: Exception) {
            log.warn("Kafka health check failed", e)
            "disconnected"
        }
    }
}
