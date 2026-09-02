package com.contentplatform.gateway.filter

import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
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
import java.util.Optional

/**
 * Checks if the authenticated JWT's jti claim is present in the Redis revocation set.
 * If found, the request is rejected with 401. Fail-open on Redis errors.
 * Unauthenticated requests pass through.
 * Order: -90 — runs after Spring Security (-100).
 */
@Component
class TokenRevocationFilter(
    private val redisTemplate: ReactiveStringRedisTemplate
) : WebFilter, Ordered {

    companion object {
        private val log = LoggerFactory.getLogger(TokenRevocationFilter::class.java)
        private val NO_AUTH = Optional.empty<JwtAuthenticationToken>()
    }

    override fun getOrder(): Int = -90 // After Spring Security (-100)

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        return ReactiveSecurityContextHolder.getContext()
            .map { ctx -> Optional.ofNullable(ctx.authentication as? JwtAuthenticationToken) }
            .defaultIfEmpty(NO_AUTH)
            .flatMap { optAuth ->
                if (!optAuth.isPresent) {
                    return@flatMap chain.filter(exchange)
                }

                val jwtAuth = optAuth.get()
                val jti = jwtAuth.token.claims["jti"] as? String
                if (jti == null) {
                    log.warn("JWT has no jti claim, rejecting request")
                    return@flatMap writeUnauthorized(exchange, "Token has no jti claim")
                }

                redisTemplate.opsForValue()
                    .get("revoked:$jti")
                    .defaultIfEmpty("")
                    .flatMap { value ->
                        if (value.isNotEmpty()) {
                            log.warn("Token jti={} is revoked, rejecting request", jti)
                            writeUnauthorized(exchange, "Token has been revoked")
                        } else {
                            chain.filter(exchange)
                        }
                    }
                    .onErrorResume { ex ->
                        log.warn("Redis error checking token revocation, fail-open: {}", ex.message)
                        chain.filter(exchange)
                    }
            }
    }

    private fun writeUnauthorized(exchange: ServerWebExchange, message: String): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.UNAUTHORIZED
        response.headers.contentType = MediaType.APPLICATION_JSON
        val requestId = exchange.attributes["requestId"] as? String ?: ""
        val json = """{"error":"token_revoked","message":"$message","request_id":"$requestId"}"""
        val buffer = response.bufferFactory().wrap(json.toByteArray(Charsets.UTF_8))
        return response.writeWith(Mono.just(buffer))
    }
}
