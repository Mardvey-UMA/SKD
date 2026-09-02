package com.contentplatform.gateway.unit

import com.contentplatform.gateway.error.GatewayErrorHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.test.StepVerifier

/**
 * Unit tests for GatewayErrorHandler (WebExceptionHandler).
 *
 * Verifies:
 * - Unhandled exceptions produce JSON error response with consistent format
 * - Response includes error code, message, and request_id
 * - 502 for service connectivity errors
 * - 500 for unexpected exceptions
 */
@Tag("unit")
class GatewayErrorHandlerTest {

    private val objectMapper = ObjectMapper()
    private val errorHandler = GatewayErrorHandler(objectMapper)

    @Test
    fun `should return 500 with JSON error body for unexpected exceptions`() {
        val request = MockServerHttpRequest.get("/api/users/me").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes["requestId"] = "req-error-500"

        val exception = RuntimeException("Something went wrong")

        val result = errorHandler.handle(exchange, exception)
        StepVerifier.create(result).verifyComplete()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(exchange.response.headers.contentType.toString()).contains("application/json")
    }

    @Test
    fun `should return 502 for service unavailable errors`() {
        val request = MockServerHttpRequest.get("/api/users/me").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes["requestId"] = "req-error-502"

        val exception = java.net.ConnectException("Connection refused")

        val result = errorHandler.handle(exchange, exception)
        StepVerifier.create(result).verifyComplete()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
    }

    @Test
    fun `should return 504 for timeout errors`() {
        val request = MockServerHttpRequest.get("/api/feed").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes["requestId"] = "req-error-504"

        val exception = io.netty.handler.timeout.ReadTimeoutException.INSTANCE

        val result = errorHandler.handle(exchange, exception)
        StepVerifier.create(result).verifyComplete()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
    }

    @Test
    fun `should include request_id in error response body`() {
        val request = MockServerHttpRequest.get("/api/users/me").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes["requestId"] = "my-request-id-123"

        val exception = RuntimeException("test error")

        val result = errorHandler.handle(exchange, exception)
        StepVerifier.create(result).verifyComplete()

        // Verify response body contains the request_id field with the correct value
        val body = (exchange.response as org.springframework.mock.http.server.reactive.MockServerHttpResponse)
            .getBodyAsString()
            .block()
        assertThat(body).isNotNull()
        val json = objectMapper.readTree(body)
        assertThat(json.get("request_id")?.asText()).isEqualTo("my-request-id-123")
        assertThat(json.get("error")?.asText()).isEqualTo("internal_server_error")
        assertThat(json.get("message")?.asText()).isEqualTo("test error")
    }
}
