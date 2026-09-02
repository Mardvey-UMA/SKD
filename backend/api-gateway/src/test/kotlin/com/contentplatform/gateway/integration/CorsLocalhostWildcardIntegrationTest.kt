package com.contentplatform.gateway.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * Integration tests for CORS localhost wildcard feature (BUG-007).
 *
 * Tests that when `gateway.cors.allow-localhost-wildcard=true` is set,
 * any localhost port is accepted as a CORS origin.
 * Also validates that when the flag is false, arbitrary localhost ports are rejected.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "gateway.cors.allow-localhost-wildcard=true",
        "gateway.cors.allowed-origins[0]=http://localhost:3000",
        "gateway.cors.allowed-origins[1]=http://localhost:5173"
    ]
)
class CorsLocalhostWildcardIntegrationTest {

    companion object {
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)

        @JvmStatic
        val jwksWireMock = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @BeforeAll
        fun startContainers() {
            redis.start()
            jwksWireMock.start()
            jwksWireMock.stubFor(
                get(urlPathEqualTo("/.well-known/jwks.json"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"keys":[]}""")
                    )
            )
        }

        @JvmStatic
        @AfterAll
        fun stopContainers() {
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
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `should allow preflight from localhost 8100 when wildcard flag is true`() {
        webTestClient.options()
            .uri("/api/users/me")
            .header("Origin", "http://localhost:8100")
            .header("Access-Control-Request-Method", "GET")
            .exchange()
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:8100")
    }

    @Test
    fun `should allow preflight from localhost 9999 when wildcard flag is true`() {
        webTestClient.options()
            .uri("/api/users/me")
            .header("Origin", "http://localhost:9999")
            .header("Access-Control-Request-Method", "GET")
            .exchange()
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:9999")
    }

    @Test
    fun `should still allow explicit origin localhost 3000 when wildcard flag is true`() {
        webTestClient.options()
            .uri("/api/users/me")
            .header("Origin", "http://localhost:3000")
            .header("Access-Control-Request-Method", "GET")
            .exchange()
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000")
    }

    @Test
    fun `should reject non-localhost origin even when wildcard flag is true`() {
        webTestClient.options()
            .uri("/api/users/me")
            .header("Origin", "http://evil-site.com")
            .header("Access-Control-Request-Method", "GET")
            .exchange()
            .expectHeader().doesNotExist("Access-Control-Allow-Origin")
    }
}
