package com.contentplatform.gateway.filter

import com.contentplatform.gateway.routing.RouteResolver
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.netty.handler.timeout.ReadTimeoutException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Streaming reverse proxy using WebClient.
 * Forwards requests to the resolved target service.
 * Returns 502 on connection failure or unserviceable backend response.
 * Returns 504 on timeout.
 * Runs at LOWEST_PRECEDENCE so all other filters run first.
 */
@Component
class ProxyFilter(
    private val routeResolver: RouteResolver,
    private val webClientBuilder: WebClient.Builder,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry
) : WebFilter, Ordered {

    companion object {
        private val log = LoggerFactory.getLogger(ProxyFilter::class.java)
    }

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val request = exchange.request
        val path = request.path.value()

        val route = routeResolver.resolve(path)
        if (route == null) {
            // No proxy route — let DispatcherHandler handle it (e.g., RouterFunction endpoints like /health)
            return chain.filter(exchange)
        }

        val targetUrl = route.target + path + (request.uri.rawQuery?.let { "?$it" } ?: "")

        val webClient = webClientBuilder.build()

        return webClient.method(request.method)
            .uri(targetUrl)
            .headers { headers ->
                headers.addAll(request.headers)
                headers.remove("Host")
                headers.remove("Transfer-Encoding")
                headers.remove("Connection")
            }
            .body(request.body, DataBuffer::class.java)
            .exchangeToMono { clientResponse ->
                recordRequest(route.pathPrefix, clientResponse.statusCode().value())
                exchange.response.statusCode = clientResponse.statusCode()
                exchange.response.headers.putAll(clientResponse.headers().asHttpHeaders())
                exchange.response.headers.remove("Transfer-Encoding")
                exchange.response.headers.remove("Connection")
                exchange.response.writeWith(clientResponse.bodyToFlux(DataBuffer::class.java))
            }
            .onErrorResume(WebClientRequestException::class.java) { ex ->
                when {
                    isTimeoutException(ex) -> {
                        recordRequest(route.pathPrefix, 504)
                        log.warn("Backend read timeout for path={}", path)
                        writeErrorResponse(exchange, HttpStatus.GATEWAY_TIMEOUT, "gateway_timeout", "Gateway timeout")
                    }
                    else -> {
                        recordRequest(route.pathPrefix, 502)
                        log.warn("Backend connection failed for path={}: {}", path, ex.message)
                        writeErrorResponse(exchange, HttpStatus.BAD_GATEWAY, "service_unavailable", "Service unavailable")
                    }
                }
            }
            .onErrorResume(ReadTimeoutException::class.java) { _ ->
                recordRequest(route.pathPrefix, 504)
                log.warn("Backend read timeout for path={}", path)
                writeErrorResponse(exchange, HttpStatus.GATEWAY_TIMEOUT, "gateway_timeout", "Gateway timeout")
            }
            .onErrorResume { ex ->
                when {
                    isTimeoutException(ex) -> {
                        recordRequest(route.pathPrefix, 504)
                        log.warn("Backend timeout for path={}: {}", path, ex.message)
                        writeErrorResponse(exchange, HttpStatus.GATEWAY_TIMEOUT, "gateway_timeout", "Gateway timeout")
                    }
                    else -> {
                        recordRequest(route.pathPrefix, 502)
                        log.error("Unexpected proxy error for path={}: {}", path, ex.message)
                        writeErrorResponse(exchange, HttpStatus.BAD_GATEWAY, "service_unavailable", "Service unavailable")
                    }
                }
            }
    }

    private fun recordRequest(pathPrefix: String, statusCode: Int) {
        meterRegistry.counter(
            "gateway_requests_total",
            "service", pathPrefix.trimEnd('/').substringAfterLast('/'),
            "status", when (statusCode / 100) {
                2 -> "2xx"
                4 -> "4xx"
                else -> "5xx"
            }
        ).increment()
    }

    private fun isTimeoutException(ex: Throwable): Boolean {
        if (ex is ReadTimeoutException) return true
        if (ex is java.util.concurrent.TimeoutException) return true
        val cause = ex.cause
        if (cause != null && cause !== ex) return isTimeoutException(cause)
        return false
    }

    private fun writeErrorResponse(
        exchange: ServerWebExchange,
        status: HttpStatus,
        errorCode: String,
        message: String
    ): Mono<Void> {
        val response = exchange.response
        if (response.isCommitted) return Mono.empty()
        response.statusCode = status
        response.headers.contentType = MediaType.APPLICATION_JSON
        val requestId = exchange.attributes["requestId"] as? String ?: ""
        val body = objectMapper.writeValueAsBytes(
            mapOf(
                "error" to errorCode,
                "message" to message,
                "request_id" to requestId
            )
        )
        val buffer = response.bufferFactory().wrap(body)
        return response.writeWith(Mono.just(buffer))
    }
}
