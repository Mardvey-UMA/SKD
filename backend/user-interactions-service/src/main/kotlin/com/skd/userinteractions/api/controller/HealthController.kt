package com.skd.userinteractions.api.controller

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

@RestController
class HealthController(
    private val dataSource: DataSource,
    private val kafkaTemplate: KafkaTemplate<*, *>
) {

    companion object {
        private val log = LoggerFactory.getLogger(HealthController::class.java)
    }

    @GetMapping("/health")
    fun health(): Map<String, Any> {
        val dbStatus = checkDatabase()
        val kafkaStatus = checkKafka()

        return mapOf(
            "status" to "ok",
            "service" to "user-interactions-service",
            "checks" to mapOf(
                "database" to dbStatus,
                "kafka" to kafkaStatus
            )
        )
    }

    private fun checkDatabase(): String {
        return try {
            dataSource.connection.use { connection ->
                if (connection.isValid(1)) "connected" else "disconnected"
            }
        } catch (e: Exception) {
            log.warn("Database health check failed: {}", e.message)
            "disconnected"
        }
    }

    private fun checkKafka(): String {
        return try {
            kafkaTemplate.producerFactory.createProducer().use { producer ->
                producer.partitionsFor("user.interactions.batch")
            }
            "connected"
        } catch (e: Exception) {
            log.warn("Kafka health check failed: {}", e.message)
            "disconnected"
        }
    }
}
