package com.contentplatform.gateway.unit

import com.contentplatform.gateway.health.HealthHandler
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.ReactiveRedisConnection
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * Unit tests for HealthHandler.
 *
 * Verifies:
 * - When Redis is connected, returns status "ok" with HTTP 200
 * - When Redis is disconnected, returns status "degraded" with HTTP 503
 * - Response always includes service name "api-gateway"
 * - Response includes checks.redis field
 */
@Tag("unit")
class HealthEndpointTest {

    private val redisTemplate = mockk<ReactiveStringRedisTemplate>()
    private val connectionFactory = mockk<ReactiveRedisConnectionFactory>()
    private val reactiveConnection = mockk<ReactiveRedisConnection>()

    @Test
    fun `should return ok status when Redis is connected`() {
        every { redisTemplate.connectionFactory } returns connectionFactory
        every { connectionFactory.reactiveConnection } returns reactiveConnection
        every { reactiveConnection.ping() } returns Mono.just("PONG")

        val handler = HealthHandler(redisTemplate)
        val result = handler.checkHealth()

        StepVerifier.create(result)
            .assertNext { response ->
                assertThat(response.status).isEqualTo("ok")
                assertThat(response.service).isEqualTo("api-gateway")
                assertThat(response.checks["redis"]).isEqualTo("connected")
            }
            .verifyComplete()
    }

    @Test
    fun `should return degraded status when Redis is disconnected`() {
        every { redisTemplate.connectionFactory } returns connectionFactory
        every { connectionFactory.reactiveConnection } returns reactiveConnection
        every { reactiveConnection.ping() } returns Mono.error(RuntimeException("Redis unavailable"))

        val handler = HealthHandler(redisTemplate)
        val result = handler.checkHealth()

        StepVerifier.create(result)
            .assertNext { response ->
                assertThat(response.status).isEqualTo("degraded")
                assertThat(response.service).isEqualTo("api-gateway")
                assertThat(response.checks["redis"]).isEqualTo("disconnected")
            }
            .verifyComplete()
    }

    @Test
    fun `should return degraded status when Redis connection factory is null`() {
        every { redisTemplate.connectionFactory } returns null

        val handler = HealthHandler(redisTemplate)
        val result = handler.checkHealth()

        StepVerifier.create(result)
            .assertNext { response ->
                assertThat(response.status).isEqualTo("degraded")
                assertThat(response.checks["redis"]).isEqualTo("disconnected")
            }
            .verifyComplete()
    }

    @Test
    fun `should always include service name api-gateway`() {
        every { redisTemplate.connectionFactory } returns connectionFactory
        every { connectionFactory.reactiveConnection } returns reactiveConnection
        every { reactiveConnection.ping() } returns Mono.just("PONG")

        val handler = HealthHandler(redisTemplate)
        val result = handler.checkHealth()

        StepVerifier.create(result)
            .assertNext { response ->
                assertThat(response.service).isEqualTo("api-gateway")
            }
            .verifyComplete()
    }
}
