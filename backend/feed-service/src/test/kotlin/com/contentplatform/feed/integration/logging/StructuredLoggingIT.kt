package com.contentplatform.feed.integration.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.contentplatform.feed.integration.IntegrationTestBase
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.get as wmGet
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.slf4j.Logger.ROOT_LOGGER_NAME
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Integration tests verifying that MdcPopulationFilter populates request_id/user_id
 * in MDC so log events emitted during request processing carry those fields.
 *
 * Uses the fallback path (rec-system 500) to trigger WARN logs on the request thread.
 */
@Tag("integration")
class StructuredLoggingIT : IntegrationTestBase() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Value("\${gateway.hmac-secret}")
    lateinit var hmacSecret: String

    companion object {
        val recSystemWireMock = WireMockServer(wireMockConfig().dynamicPort())
        val contentAggWireMock = WireMockServer(wireMockConfig().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun startWireMock() {
            recSystemWireMock.start()
            contentAggWireMock.start()
        }

        @AfterAll
        @JvmStatic
        fun stopWireMock() {
            recSystemWireMock.stop()
            contentAggWireMock.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun wireMockProperties(registry: DynamicPropertyRegistry) {
            registry.add("services.rec-system.url") { "http://localhost:${recSystemWireMock.port()}" }
            registry.add("services.content-aggregation.url") { "http://localhost:${contentAggWireMock.port()}" }
        }
    }

    private val capturedEvents = CopyOnWriteArrayList<ILoggingEvent>()
    private lateinit var capturingAppender: CapturingAppender

    @BeforeEach
    fun setUp() {
        capturedEvents.clear()
        capturingAppender = CapturingAppender(capturedEvents)
        capturingAppender.start()
        (LoggerFactory.getLogger(ROOT_LOGGER_NAME) as Logger).addAppender(capturingAppender)
        recSystemWireMock.resetAll()
        contentAggWireMock.resetAll()
    }

    @AfterEach
    fun tearDown() {
        (LoggerFactory.getLogger(ROOT_LOGGER_NAME) as Logger).detachAppender(capturingAppender)
    }

    private fun computeHmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    private fun buildAuthHeaders(userId: String, requestId: String): HttpHeaders {
        val roles = "USER"
        val tier = "free"
        val signature = computeHmac("$userId|$roles|$tier|$requestId")
        return HttpHeaders().apply {
            set("X-User-Id", userId)
            set("X-User-Roles", roles)
            set("X-Subscription-Tier", tier)
            set("X-Request-Id", requestId)
            set("X-Gateway-Signature", signature)
        }
    }

    @Test
    fun `WARN log emitted during request fallback carries request_id from MDC`() {
        val userId = UUID.randomUUID().toString()
        val requestId = UUID.randomUUID().toString()

        // Force rec-system failure so FeedService emits WARN on the request thread
        recSystemWireMock.stubFor(
            post(urlPathEqualTo("/recommendations"))
                .willReturn(aResponse().withStatus(500))
        )
        recSystemWireMock.stubFor(
            wmGet(urlEqualTo("/recommendations/cold-start?count=120"))
                .willReturn(aResponse().withStatus(500))
        )

        val headers = buildAuthHeaders(userId = userId, requestId = requestId)
        restTemplate.exchange("/api/feed", HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)

        await().atMost(Duration.ofSeconds(3)).untilAsserted {
            val eventsWithRequestId = capturedEvents.filter { event ->
                event.mdcPropertyMap["request_id"] == requestId
            }
            assertThat(eventsWithRequestId)
                .describedAs("Expected at least one log event with request_id=$requestId in MDC")
                .isNotEmpty
        }
    }

    @Test
    fun `WARN log emitted during request fallback carries user_id from MDC`() {
        val userId = UUID.randomUUID().toString()
        val requestId = UUID.randomUUID().toString()

        recSystemWireMock.stubFor(
            post(urlPathEqualTo("/recommendations"))
                .willReturn(aResponse().withStatus(500))
        )
        recSystemWireMock.stubFor(
            wmGet(urlEqualTo("/recommendations/cold-start?count=120"))
                .willReturn(aResponse().withStatus(500))
        )

        val headers = buildAuthHeaders(userId = userId, requestId = requestId)
        restTemplate.exchange("/api/feed", HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)

        await().atMost(Duration.ofSeconds(3)).untilAsserted {
            val eventsWithUserId = capturedEvents.filter { event ->
                event.mdcPropertyMap["user_id"] == userId
            }
            assertThat(eventsWithUserId)
                .describedAs("Expected at least one log event with user_id=$userId in MDC")
                .isNotEmpty
        }
    }

    @Test
    fun `log events after request completes do not carry request_id from previous request`() {
        val userId = UUID.randomUUID().toString()
        val requestId = UUID.randomUUID().toString()

        recSystemWireMock.stubFor(
            post(urlPathEqualTo("/recommendations"))
                .willReturn(aResponse().withStatus(500))
        )
        recSystemWireMock.stubFor(
            wmGet(urlEqualTo("/recommendations/cold-start?count=120"))
                .willReturn(aResponse().withStatus(500))
        )

        val headers = buildAuthHeaders(userId = userId, requestId = requestId)
        restTemplate.exchange("/api/feed", HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)

        // Wait for request to complete and events to be captured
        await().atMost(Duration.ofSeconds(3)).until {
            capturedEvents.any { it.mdcPropertyMap["request_id"] == requestId }
        }

        // Verify MDC was cleared: events captured AFTER the request (health check) lack request_id
        val eventsBeforeCount = capturedEvents.size
        restTemplate.getForEntity("/health", String::class.java)

        // Any events from the health check should not have the previous request's request_id
        val laterEvents = capturedEvents.drop(eventsBeforeCount)
        val pollutedEvents = laterEvents.filter { it.mdcPropertyMap["request_id"] == requestId }
        assertThat(pollutedEvents).isEmpty()
    }

    private class CapturingAppender(
        private val events: CopyOnWriteArrayList<ILoggingEvent>
    ) : AppenderBase<ILoggingEvent>() {
        override fun append(event: ILoggingEvent) {
            // Freeze the MDC snapshot now (ILoggingEvent may be mutable)
            event.prepareForDeferredProcessing()
            events.add(event)
        }
    }
}
