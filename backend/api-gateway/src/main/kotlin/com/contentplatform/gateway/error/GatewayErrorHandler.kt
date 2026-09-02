package com.contentplatform.gateway.error

import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.handler.timeout.ReadTimeoutException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono
import java.net.ConnectException

/**
 * Global WebExceptionHandler that converts unhandled exceptions to consistent JSON error responses.
 * - ConnectException -> 502 Bad Gateway
 * - ReadTimeoutException -> 504 Gateway Timeout
 * - RuntimeException and others -> 500 Internal Server Error
 *
 * Always includes error code, message, and request_id in response body.
 */
@Component
@Order(-2)
class GatewayErrorHandler(
    private val objectMapper: ObjectMapper
) : WebExceptionHandler {

    companion object {
        private val log = LoggerFactory.getLogger(GatewayErrorHandler::class.java)
    }

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        val (status, errorCode) = when (ex) {
            is ConnectException -> HttpStatus.BAD_GATEWAY to "service_unavailable"
            is ReadTimeoutException -> HttpStatus.GATEWAY_TIMEOUT to "gateway_timeout"
            else -> HttpStatus.INTERNAL_SERVER_ERROR to "internal_server_error"
        }

        log.error("Unhandled exception: status={}, error={}", status, ex.message, ex)

        val response = exchange.response
        if (response.isCommitted) {
            return Mono.error(ex)
        }

        response.statusCode = status
        response.headers.contentType = MediaType.APPLICATION_JSON

        val requestId = exchange.attributes["requestId"] as? String ?: ""
        val body = mapOf(
            "error" to errorCode,
            "message" to (ex.message ?: "An unexpected error occurred"),
            "request_id" to requestId
        )
        val bytes = objectMapper.writeValueAsBytes(body)
        val buffer: DataBuffer = response.bufferFactory().wrap(bytes)
        return response.writeWith(Mono.just(buffer))
    }
}
