package com.contentplatform.feed.api.controller

import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

@RestController
class HealthController(
    private val dataSource: DataSource,
    private val redisConnectionFactory: RedisConnectionFactory
) {

    companion object {
        private val log = LoggerFactory.getLogger(HealthController::class.java)
    }

    @GetMapping("/health")
    fun health(): Map<String, Any> {
        val dbStatus = try {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT 1")
                }
            }
            "connected"
        } catch (e: Exception) {
            log.warn("Database health check failed: {}", e.message)
            "error"
        }

        val redisStatus = try {
            redisConnectionFactory.connection.use { conn ->
                conn.ping()
            }
            "connected"
        } catch (e: Exception) {
            log.warn("Redis health check failed: {}", e.message)
            "error"
        }

        return mapOf(
            "status" to "ok",
            "service" to "feed-service",
            "checks" to mapOf(
                "database" to dbStatus,
                "redis" to redisStatus
            )
        )
    }
}
