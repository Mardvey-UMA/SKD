package com.contentplatform.gateway.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * Smoke tests for the API Gateway.
 *
 * Verifies:
 * - Spring application context loads successfully with all beans
 * - /health endpoint returns 200
 * - WebTestClient is available and functional
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewaySmokeTest {

    companion object {
        private val rsaKey: RSAKey = RSAKeyGenerator(2048)
            .keyID("smoke-test-key")
            .generate()
        private val jwksJson: String = JWKSet(rsaKey.toPublicJWK()).toString()

        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)

        @JvmStatic
        val jwksWireMock = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @BeforeAll
        fun startInfrastructure() {
            redis.start()
            jwksWireMock.start()

            jwksWireMock.stubFor(
                get(urlPathEqualTo("/.well-known/jwks.json"))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(jwksJson)
                    )
            )
        }

        @JvmStatic
        @AfterAll
        fun stopInfrastructure() {
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
            registry.add("gateway.hmac-secret") { "smoke-test-hmac-secret" }
        }
    }

    @Autowired
    lateinit var applicationContext: ApplicationContext

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `application context should load successfully`() {
        // If this test runs, the context loaded without errors
        org.assertj.core.api.Assertions.assertThat(applicationContext).isNotNull()
    }

    @Test
    fun `health endpoint should return 200`() {
        webTestClient.get()
            .uri("/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("ok")
            .jsonPath("$.service").isEqualTo("api-gateway")
    }

    @Test
    fun `health endpoint should be accessible without authentication`() {
        webTestClient.get()
            .uri("/health")
            .exchange()
            .expectStatus().isOk
    }
}
