package com.contentplatform.gateway.unit

import com.contentplatform.gateway.filter.RequestIdFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.core.Ordered
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

@Tag("unit")
class RequestIdFilterTest {

    private val filter = RequestIdFilter()

    @Test
    fun `should add X-Request-Id header when not present`() {
        val request = MockServerHttpRequest.get("/api/users/me").build()
        val exchange = MockServerWebExchange.from(request)

        var capturedExchange: ServerWebExchange? = null
        val chain = WebFilterChain { ex ->
            capturedExchange = ex
            Mono.empty()
        }

        filter.filter(exchange, chain).block()

        val requestId = capturedExchange?.request?.headers?.getFirst("X-Request-Id")
        assertThat(requestId).isNotNull()
        // Verify it is a valid UUID
        assertThat(UUID.fromString(requestId)).isNotNull()
    }

    @Test
    fun `should generate unique X-Request-Id for each request`() {
        val ids = mutableSetOf<String>()

        repeat(10) {
            val request = MockServerHttpRequest.get("/api/users/me").build()
            val exchange = MockServerWebExchange.from(request)

            val chain = WebFilterChain { ex ->
                val id = ex.request.headers.getFirst("X-Request-Id")
                if (id != null) ids.add(id)
                Mono.empty()
            }

            filter.filter(exchange, chain).block()
        }

        assertThat(ids).hasSize(10)
    }

    @Test
    fun `should set X-Request-Id as exchange attribute`() {
        val request = MockServerHttpRequest.get("/api/feed").build()
        val exchange = MockServerWebExchange.from(request)

        filter.filter(exchange, WebFilterChain { Mono.empty() }).block()

        val attrId = exchange.getAttribute<String>("requestId")
        assertThat(attrId).isNotNull()
        assertThat(UUID.fromString(attrId)).isNotNull()
    }

    @Test
    fun `should add X-Request-Id to response headers`() {
        val request = MockServerHttpRequest.get("/api/users/me").build()
        val exchange = MockServerWebExchange.from(request)

        filter.filter(exchange, WebFilterChain { Mono.empty() }).block()

        val responseId = exchange.response.headers.getFirst("X-Request-Id")
        assertThat(responseId).isNotNull()
        assertThat(UUID.fromString(responseId)).isNotNull()
    }

    @Test
    fun `filter order should be HIGHEST_PRECEDENCE plus 10`() {
        assertThat(filter.order).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10)
    }

    @Test
    fun `should always generate a fresh UUID even when caller sends X-Request-Id header`() {
        val incomingId = "caller-provided-id-00000000-0000-0000-0000-000000000000"
        val request = MockServerHttpRequest.get("/api/users/me")
            .header("X-Request-Id", incomingId)
            .build()
        val exchange = MockServerWebExchange.from(request)

        var capturedExchange: ServerWebExchange? = null
        val chain = WebFilterChain { ex ->
            capturedExchange = ex
            Mono.empty()
        }

        filter.filter(exchange, chain).block()

        // The gateway always generates its own UUID — it does NOT echo the caller's header
        val downstreamId = capturedExchange?.request?.headers?.getFirst("X-Request-Id")
        assertThat(downstreamId).isNotNull()
        // Must be a valid UUID
        assertThat(UUID.fromString(downstreamId)).isNotNull()
        // Must NOT be the original caller-provided value (gateway generates fresh)
        assertThat(downstreamId).isNotEqualTo(incomingId)
    }
}
