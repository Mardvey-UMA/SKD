package com.contentplatform.feed.infrastructure.health

import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import javax.sql.DataSource

class FeedSchemaHealthIndicator(
    private val dataSource: DataSource
) : HealthIndicator {

    companion object {
        private val log = LoggerFactory.getLogger(FeedSchemaHealthIndicator::class.java)
        private const val PROBE_QUERY = "SELECT 1 FROM feed.feed_requests LIMIT 1"
    }

    override fun health(): Health {
        return try {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(PROBE_QUERY)
                }
            }
            Health.up().build()
        } catch (e: Exception) {
            log.warn("feed schema health check failed: {}", e.message)
            Health.down(e)
                .withDetail("query", PROBE_QUERY)
                .build()
        }
    }
}
