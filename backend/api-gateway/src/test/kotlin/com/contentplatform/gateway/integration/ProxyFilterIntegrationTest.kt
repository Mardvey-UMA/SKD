package com.contentplatform.gateway.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
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

/**
 * Integration tests for ProxyFilter + RouteResolver.
 * Uses WireMock as a backend service target and Redis (Testcontainers).
 * Verifies:
 * - Requests are forwarded to the correct backend
 * - Response body and status are streamed back
 * - 502 returned when backend is down
 * - 504 returned when backend times out
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProxyFilterIntegrationTest {

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

            // Stub JWKS endpoint so Spring Security can start
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
            // Override route target to point to WireMock
            registry.add("gateway.routes[0].path-prefix") { "/api/auth" }
            registry.add("gateway.routes[0].target") { "http://localhost:${backendWireMock.port()}" }
            registry.add("gateway.routes[0].public") { "true" }
            registry.add("gateway.routes[0].strip-prefix") { "false" }
            // Dead port route for 502 testing — nothing listens on port 1
            registry.add("gateway.routes[1].path-prefix") { "/api/dead" }
            registry.add("gateway.routes[1].target") { "http://localhost:1" }
            registry.add("gateway.routes[1].public") { "true" }
            registry.add("gateway.routes[1].strip-prefix") { "false" }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun resetWireMock() {
        backendWireMock.resetMappings()
        // Re-stub JWKS
        jwksWireMock.stubFor(
            get(urlPathEqualTo("/.well-known/jwks.json"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"keys":[]}""")
                )
        )
    }

    @Test
    fun `should forward GET request to backend and return response`() {
        backendWireMock.stubFor(
            get(urlPathEqualTo("/api/auth/status"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"status":"ok"}""")
                )
        )

        webTestClient.get()
            .uri("/api/auth/status")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { body ->
                assertThat(body).contains("ok")
            }

        backendWireMock.verify(getRequestedFor(urlPathEqualTo("/api/auth/status")))
    }

    @Test
    fun `should forward POST request with body to backend`() {
        backendWireMock.stubFor(
            post(urlPathEqualTo("/api/auth/login"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"token":"abc123"}""")
                )
        )

        webTestClient.post()
            .uri("/api/auth/login")
            .header("Content-Type", "application/json")
            .bodyValue("""{"email":"test@test.com","password":"pass"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { body ->
                assertThat(body).contains("abc123")
            }

        backendWireMock.verify(postRequestedFor(urlPathEqualTo("/api/auth/login")))
    }

    @Test
    fun `should return 502 when backend is not reachable`() {
        // Route /api/dead points to http://localhost:1 — nothing listens there,
        // guaranteeing a ConnectException which the proxy maps to 502.
        webTestClient.get()
            .uri("/api/dead/anything")
            .exchange()
            .expectStatus().value { status ->
                assertThat(status).isEqualTo(502)
            }
    }

    @Test
    fun `should return 504 when backend times out`() {
        backendWireMock.stubFor(
            get(urlPathEqualTo("/api/auth/slow"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withFixedDelay(10000) // 10 seconds, exceeds 2s test timeout
                )
        )

        webTestClient.get()
            .uri("/api/auth/slow")
            .exchange()
            .expectStatus().value { status ->
                assertThat(status).isEqualTo(504)
            }
    }

    @Test
    fun `should preserve response status code from backend`() {
        backendWireMock.stubFor(
            get(urlPathEqualTo("/api/auth/not-found"))
                .willReturn(
                    aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":"not_found"}""")
                )
        )

        webTestClient.get()
            .uri("/api/auth/not-found")
            .exchange()
            .expectStatus().isNotFound
    }
}
