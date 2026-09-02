package com.contentplatform.user.api.controller

import com.contentplatform.user.api.dto.HealthResponse
import org.apache.kafka.clients.admin.AdminClient
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

@RestController
class HealthController(
    private val dataSource: DataSource,
    private val kafkaAdmin: KafkaAdmin
) {

    companion object {
        private val log = LoggerFactory.getLogger(HealthController::class.java)
    }

    @GetMapping("/health")
    fun health(): HealthResponse {
        val checks = mapOf(
            "database" to checkDatabase(),
            "kafka" to checkKafka()
        )
        val status = if (checks.values.all { it == "connected" }) "ok" else "degraded"
        return HealthResponse(status = status, service = "user-service", checks = checks)
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

    private fun checkKafka(): String {
        return try {
            AdminClient.create(kafkaAdmin.configurationProperties).use { client ->
                client.listTopics().names().get()
            }
            "connected"
        } catch (e: Exception) {
            log.warn("Kafka health check failed", e)
            "disconnected"
        }
    }
}
