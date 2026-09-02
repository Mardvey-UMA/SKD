package com.contentplatform.gateway.filter

import com.contentplatform.gateway.config.GatewayProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.Optional

/**
 * Per-user rate limiting using Redis INCR counter.
 * Fail-open: if Redis is unavailable, allows request through.
 * Returns 429 with Retry-After header when limit exceeded.
 * Order: -85 — after TokenRevocationFilter (-90), before HeaderEnrichmentFilter (-80).
 */
@Component
class RateLimitFilter(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val properties: GatewayProperties
) : WebFilter, Ordered {

    companion object {
        private val log = LoggerFactory.getLogger(RateLimitFilter::class.java)
        private val objectMapper = ObjectMapper()
        private val NO_AUTH: Optional<JwtAuthenticationToken> = Optional.empty()
    }

    override fun getOrder(): Int = -85

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        return ReactiveSecurityContextHolder.getContext()
            .map { ctx -> Optional.ofNullable(ctx.authentication as? JwtAuthenticationToken) }
            .defaultIfEmpty(NO_AUTH)
            .flatMap { optAuth ->
                if (!optAuth.isPresent) {
                    return@flatMap chain.filter(exchange)
                }
                val jwtAuth = optAuth.get()
                val userId = jwtAuth.token.subject ?: return@flatMap chain.filter(exchange)
                val key = "rate:$userId"
                val maxAllowed = properties.rateLimit.capacity + properties.rateLimit.overdraft

                redisTemplate.opsForValue().increment(key)
                    .flatMap { count ->
                        redisTemplate.expire(key, Duration.ofSeconds(properties.rateLimit.windowSeconds))
                            .thenReturn(count)
                    }
                    .flatMap { count ->
                        if (count <= maxAllowed) {
                            chain.filter(exchange)
                        } else {
                            log.warn("Rate limit exceeded for userId={}, count={}", userId, count)
                            rejectWithTooManyRequests(exchange)
                        }
                    }
                    .onErrorResume { ex ->
                        log.warn("Redis unavailable for rate limiting, fail-open: {}", ex.message)
                        chain.filter(exchange)
                    }
            }
    }

    private fun rejectWithTooManyRequests(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.TOO_MANY_REQUESTS
        response.headers.contentType = MediaType.APPLICATION_JSON
        response.headers.set("Retry-After", properties.rateLimit.windowSeconds.toString())

        val requestId = exchange.attributes[RequestIdFilter.REQUEST_ID_ATTR] as? String ?: ""
        val body = mapOf(
            "error" to "rate_limit_exceeded",
            "message" to "Too many requests. Please slow down.",
            "request_id" to requestId
        )
        val bytes = objectMapper.writeValueAsBytes(body)
        val buffer: DataBuffer = response.bufferFactory().wrap(bytes)
        return response.writeWith(Mono.just(buffer))
    }
}
