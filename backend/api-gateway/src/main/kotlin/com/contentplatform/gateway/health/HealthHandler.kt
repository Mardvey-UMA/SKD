package com.contentplatform.gateway.health

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

data class HealthResponse(
    val status: String,
    val service: String = "api-gateway",
    val checks: Map<String, String>
)

/**
 * Handles health check logic.
 * Pings Redis to determine connectivity.
 * Returns status "ok" (200) when Redis is connected, "degraded" (503) when not.
 */
@Component
class HealthHandler(
    private val redisTemplate: ReactiveStringRedisTemplate
) {

    companion object {
        private val log = LoggerFactory.getLogger(HealthHandler::class.java)
    }

    fun checkHealth(): Mono<HealthResponse> {
        val factory = redisTemplate.connectionFactory
            ?: return Mono.just(
                HealthResponse(
                    status = "degraded",
                    checks = mapOf("redis" to "disconnected")
                )
            )

        return factory.reactiveConnection.ping()
            .map {
                HealthResponse(
                    status = "ok",
                    checks = mapOf("redis" to "connected")
                )
            }
            .onErrorResume { ex ->
                log.warn("Redis ping failed: {}", ex.message)
                Mono.just(
                    HealthResponse(
                        status = "degraded",
                        checks = mapOf("redis" to "disconnected")
                    )
                )
            }
    }
}
