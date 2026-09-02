package com.contentplatform.gateway.filter

import com.contentplatform.gateway.config.GatewayProperties
import com.contentplatform.gateway.security.HmacSigner
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.Optional

/**
 * Enriches authenticated requests with user headers derived from JWT claims.
 * Sets: X-User-Id, X-User-Roles, X-Subscription-Tier, X-Request-Id, X-Gateway-Signature.
 * Unauthenticated requests pass through without enrichment.
 * Order: -80 — runs after TokenRevocationFilter (-90) and Spring Security (-100).
 */
@Component
class HeaderEnrichmentFilter(
    private val gatewayProperties: GatewayProperties
) : WebFilter, Ordered {

    companion object {
        private val log = LoggerFactory.getLogger(HeaderEnrichmentFilter::class.java)
        private val NO_AUTH = Optional.empty<JwtAuthenticationToken>()
    }

    override fun getOrder(): Int = -80 // After TokenRevocationFilter (-90)

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        return ReactiveSecurityContextHolder.getContext()
            .map { ctx -> Optional.ofNullable(ctx.authentication as? JwtAuthenticationToken) }
            .defaultIfEmpty(NO_AUTH)
            .flatMap { optAuth ->
                if (!optAuth.isPresent) {
                    return@flatMap chain.filter(exchange)
                }

                val jwtAuth = optAuth.get()
                val claims = jwtAuth.token.claims
                val userId = claims["sub"] as? String ?: ""
                @Suppress("UNCHECKED_CAST")
                val roles = (claims["roles"] as? List<String>)?.joinToString(",") ?: ""
                val tier = claims["subscription_tier"] as? String ?: ""
                val requestId = exchange.attributes[RequestIdFilter.REQUEST_ID_ATTR] as? String ?: ""

                val signer = HmacSigner(gatewayProperties.hmacSecret)
                val signature = signer.sign(userId, roles, tier, requestId)

                log.debug("Enriching request for userId={}", userId)

                val mutatedExchange = exchange.mutate()
                    .request { req ->
                        req.header("X-User-Id", userId)
                        req.header("X-User-Roles", roles)
                        req.header("X-Subscription-Tier", tier)
                        req.header("X-Request-Id", requestId)
                        req.header("X-Gateway-Signature", signature)
                    }
                    .build()

                chain.filter(mutatedExchange)
            }
    }
}
