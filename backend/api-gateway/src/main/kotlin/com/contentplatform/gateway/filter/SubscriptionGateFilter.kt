package com.contentplatform.gateway.filter

import com.contentplatform.gateway.routing.RouteResolver
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.io.buffer.DataBuffer
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
 * Checks that a user's subscription tier satisfies the route's requiredRoles.
 * If route requires SUBSCRIBER and user's subscription_tier is "free", returns 403.
 * Unauthenticated requests and routes without requiredRoles pass through.
 * Order: -83 — after RateLimitFilter (-85), before HeaderEnrichmentFilter (-80).
 */
@Component
class SubscriptionGateFilter(
    private val routeResolver: RouteResolver
) : WebFilter, Ordered {

    companion object {
        private val log = LoggerFactory.getLogger(SubscriptionGateFilter::class.java)
        private val objectMapper = ObjectMapper()
        private val NO_AUTH: Optional<JwtAuthenticationToken> = Optional.empty()
    }

    override fun getOrder(): Int = -83

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.value()
        val route = routeResolver.resolve(path)

        if (route == null || route.requiredRoles.isEmpty()) {
            return chain.filter(exchange)
        }

        return ReactiveSecurityContextHolder.getContext()
            .map { ctx -> Optional.ofNullable(ctx.authentication as? JwtAuthenticationToken) }
            .defaultIfEmpty(NO_AUTH)
            .flatMap { optAuth ->
                if (!optAuth.isPresent) {
                    return@flatMap chain.filter(exchange)
                }

                val jwtAuth = optAuth.get()
                val subscriptionTier = jwtAuth.token.claims["subscription_tier"] as? String ?: "free"

                val routeRequiresSubscriber = route.requiredRoles.contains("SUBSCRIBER")
                if (routeRequiresSubscriber && subscriptionTier == "free") {
                    log.warn("Subscription gate: free user denied access to path={}", path)
                    rejectWithForbidden(exchange)
                } else {
                    chain.filter(exchange)
                }
            }
    }

    private fun rejectWithForbidden(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.FORBIDDEN
        response.headers.contentType = MediaType.APPLICATION_JSON

        val requestId = exchange.attributes[RequestIdFilter.REQUEST_ID_ATTR] as? String ?: ""
        val body = mapOf(
            "error" to "subscription_required",
            "message" to "This resource requires an active subscription.",
            "request_id" to requestId
        )
        val bytes = objectMapper.writeValueAsBytes(body)
        val buffer: DataBuffer = response.bufferFactory().wrap(bytes)
        return response.writeWith(Mono.just(buffer))
    }
}
