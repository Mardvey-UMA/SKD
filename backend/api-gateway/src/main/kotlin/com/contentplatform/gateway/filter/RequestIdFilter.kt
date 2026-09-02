package com.contentplatform.gateway.filter

import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Generates X-Request-Id UUID for every request.
 * Adds to request headers (via mutate), response headers, and exchange attributes.
 * Order: HIGHEST_PRECEDENCE + 10 — runs first.
 */
@Component
class RequestIdFilter : WebFilter, Ordered {

    companion object {
        private val log = LoggerFactory.getLogger(RequestIdFilter::class.java)
        const val REQUEST_ID_ATTR = "requestId"
        const val HEADER_NAME = "X-Request-Id"
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 10

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val requestId = UUID.randomUUID().toString()

        exchange.response.headers.set(HEADER_NAME, requestId)

        val mutatedExchange = exchange.mutate()
            .request { it.header(HEADER_NAME, requestId) }
            .build()
        mutatedExchange.attributes[REQUEST_ID_ATTR] = requestId

        log.debug("Assigned requestId={}", requestId)

        return chain.filter(mutatedExchange)
    }
}
