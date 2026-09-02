package com.contentplatform.gateway.filter

import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Populates MDC with request_id and user_id for structured logging.
 *
 * Reads X-Request-Id from request headers (set by RequestIdFilter at HIGHEST+10).
 * Reads X-User-Id from request headers (set by HeaderEnrichmentFilter after auth).
 *
 * Note: MDC is ThreadLocal — not guaranteed to propagate across reactor schedulers.
 * This provides best-effort MDC population for the filter chain thread.
 *
 * Order: HIGHEST_PRECEDENCE + 20 — runs after RequestIdFilter (HIGHEST+10).
 */
@Component
class MdcPopulationFilter : WebFilter, Ordered {

    companion object {
        const val MDC_REQUEST_ID = "request_id"
        const val MDC_USER_ID = "user_id"
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 20

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val requestId = exchange.request.headers.getFirst("X-Request-Id")
            ?: exchange.attributes["requestId"]?.toString()
        val userId = exchange.request.headers.getFirst("X-User-Id")

        // Best-effort MDC population for the filter thread
        // Note: MDC is ThreadLocal — not guaranteed to propagate across reactor schedulers
        if (requestId != null) MDC.put(MDC_REQUEST_ID, requestId)
        if (userId != null) MDC.put(MDC_USER_ID, userId)

        return chain.filter(exchange)
            .doFinally {
                MDC.remove(MDC_REQUEST_ID)
                MDC.remove(MDC_USER_ID)
            }
    }
}
