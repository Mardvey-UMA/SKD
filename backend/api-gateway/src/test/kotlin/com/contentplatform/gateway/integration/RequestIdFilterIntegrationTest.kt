package com.contentplatform.gateway.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

/**
 * Integration tests for RequestIdFilter.
 * Verifies that X-Request-Id is:
 * - Present in the response headers
 * - Forwarded to the backend service (via WireMock verification)
 * - A valid UUID format
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RequestIdFilterIntegrationTest {

    companion object {
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)

        @JvmStatic
        val backendWireMock = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        val jwksWireMock = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @BeforeAll
        fun startContainers() {
            redis.start()
            backendWireMock.start()
            jwksWireMock.start()

            jwksWireMock.stubFor(
                get(urlPathEqualTo("/.well-known/jwks.json"))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"keys":[]}""")
                    )
            )
        }

        @JvmStatic
        @AfterAll
        fun stopContainers() {
            backendWireMock.stop()
            jwksWireMock.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") {
                "http://localhost:${jwksWireMock.port()}/.well-known/jwks.json"
            }
            registry.add("gateway.routes[0].path-prefix") { "/api/auth" }
            registry.add("gateway.routes[0].target") { "http://localhost:${backendWireMock.port()}" }
            registry.add("gateway.routes[0].public") { "true" }
            registry.add("gateway.routes[0].strip-prefix") { "false" }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun resetWireMock() {
        backendWireMock.resetMappings()
        backendWireMock.stubFor(
            get(urlPathEqualTo("/api/auth/test"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("""{"ok":true}""")
                )
        )
    }

    @Test
    fun `should include X-Request-Id in response headers`() {
        val responseHeaders = webTestClient.get()
            .uri("/api/auth/test")
            .exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)
            .responseHeaders

        val requestId = responseHeaders.getFirst("X-Request-Id")
        assertThat(requestId).isNotNull()
        assertThat(UUID.fromString(requestId)).isNotNull()
    }

    @Test
    fun `should forward X-Request-Id to backend service`() {
        webTestClient.get()
            .uri("/api/auth/test")
            .exchange()
            .expectStatus().isOk

        // Verify backend received X-Request-Id header
        val requests = backendWireMock.findRequestsMatching(
            getRequestedFor(urlPathEqualTo("/api/auth/test")).build()
        ).requests

        assertThat(requests).isNotEmpty
        val forwardedRequestId = requests[0].getHeader("X-Request-Id")
        assertThat(forwardedRequestId).isNotNull()
        assertThat(UUID.fromString(forwardedRequestId)).isNotNull()
    }

    @Test
    fun `should generate unique X-Request-Id for each request`() {
        val ids = mutableSetOf<String>()

        repeat(3) {
            val responseHeaders = webTestClient.get()
                .uri("/api/auth/test")
                .exchange()
                .returnResult(String::class.java)
                .responseHeaders

            val requestId = responseHeaders.getFirst("X-Request-Id")
            if (requestId != null) ids.add(requestId)
        }

        assertThat(ids).hasSize(3)
    }
}
