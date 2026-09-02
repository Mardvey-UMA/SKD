package com.contentplatform.gateway.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
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
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PrometheusMetricsIntegrationTest {

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

    @LocalManagementPort
    var managementPort: Int = 0

    private val mgmtClient: WebTestClient get() =
        WebTestClient.bindToServer().baseUrl("http://localhost:$managementPort").build()

    @BeforeEach
    fun resetWireMock() {
        backendWireMock.resetMappings()
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
    fun `prometheus endpoint should be accessible and return metrics`() {
        mgmtClient.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { body ->
                assertThat(body).contains("jvm_memory_used_bytes")
            }
    }

    @Test
    fun `gateway_requests_total counter increments after proxied 2xx request`() {
        backendWireMock.stubFor(
            get(urlPathEqualTo("/api/auth/ping"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"pong":true}""")
                )
        )

        webTestClient.get()
            .uri("/api/auth/ping")
            .exchange()
            .expectStatus().isOk

        mgmtClient.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { body ->
                assertThat(body)
                    .contains("gateway_requests_total")
                    .contains("""service="auth"""")
                    .contains("""status="2xx"""")
            }
    }

    @Test
    fun `gateway_requests_total counter records 4xx responses`() {
        backendWireMock.stubFor(
            get(urlPathEqualTo("/api/auth/protected"))
                .willReturn(
                    aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":"not_found"}""")
                )
        )

        webTestClient.get()
            .uri("/api/auth/protected")
            .exchange()
            .expectStatus().isNotFound

        mgmtClient.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { body ->
                assertThat(body)
                    .contains("gateway_requests_total")
                    .contains("""status="4xx"""")
            }
    }
}
