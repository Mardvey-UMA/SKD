package com.contentplatform.gateway.unit

import com.contentplatform.gateway.filter.MdcPopulationFilter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Tag("unit")
class MdcPopulationFilterTest {

    private val filter = MdcPopulationFilter()

    @AfterEach
    fun cleanMdc() {
        MDC.clear()
    }

    @Test
    fun `filter order should be HIGHEST_PRECEDENCE plus 20`() {
        assertThat(filter.order).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 20)
    }

    @Test
    fun `populates MDC with request_id and user_id from headers`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header("X-Request-Id", "test-request-id")
                .header("X-User-Id", "test-user-id")
                .build()
        )

        var capturedRequestId: String? = null
        var capturedUserId: String? = null

        val chain = WebFilterChain { _ ->
            capturedRequestId = MDC.get("request_id")
            capturedUserId = MDC.get("user_id")
            Mono.empty()
        }

        filter.filter(exchange, chain).block()

        assertThat(capturedRequestId).isEqualTo("test-request-id")
        assertThat(capturedUserId).isEqualTo("test-user-id")
    }

    @Test
    fun `populates MDC with request_id from exchange attribute when header absent`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test").build()
        )
        exchange.attributes["requestId"] = "attr-request-id"

        var capturedRequestId: String? = null

        val chain = WebFilterChain { _ ->
            capturedRequestId = MDC.get("request_id")
            Mono.empty()
        }

        filter.filter(exchange, chain).block()

        assertThat(capturedRequestId).isEqualTo("attr-request-id")
    }

    @Test
    fun `clears MDC after request completes`() {
        MDC.put("request_id", "old-value")

        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header("X-Request-Id", "new-request-id")
                .build()
        )

        filter.filter(exchange, WebFilterChain { Mono.empty() }).block()

        assertThat(MDC.get("request_id")).isNull()
        assertThat(MDC.get("user_id")).isNull()
    }

    @Test
    fun `handles missing headers gracefully`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test").build()
        )

        assertThatCode {
            filter.filter(exchange, WebFilterChain { Mono.empty() }).block()
        }.doesNotThrowAnyException()
    }

    @Test
    fun `does not set user_id in MDC when X-User-Id header absent`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header("X-Request-Id", "req-id-123")
                .build()
        )

        var capturedUserId: String? = "sentinel"

        val chain = WebFilterChain { _ ->
            capturedUserId = MDC.get("user_id")
            Mono.empty()
        }

        filter.filter(exchange, chain).block()

        assertThat(capturedUserId).isNull()
    }
}
