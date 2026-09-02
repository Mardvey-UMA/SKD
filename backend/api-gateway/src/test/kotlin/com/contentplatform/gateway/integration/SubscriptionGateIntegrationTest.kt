package com.contentplatform.gateway.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * Integration tests for subscription gate filtering.
 *
 * Verifies:
 * - Free user accessing /api/config (requiredRoles=SUBSCRIBER) gets 403 with JSON error
 * - Premium user accessing /api/config passes through to backend
 * - Free user accessing /api/users (no requiredRoles) passes through
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SubscriptionGateIntegrationTest {

    companion object {
        private val rsaKey: RSAKey = RSAKeyGenerator(2048)
            .keyID("test-key-id")
            .generate()
        private val jwksJson: String = JWKSet(rsaKey.toPublicJWK()).toString()
        private val signer = RSASSASigner(rsaKey)

        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)

        @JvmStatic
        val jwksWireMock = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        val backendWireMock = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @BeforeAll
        fun startContainers() {
            redis.start()
            jwksWireMock.start()
            backendWireMock.start()

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
        fun stopContainers() {
            jwksWireMock.stop()
            backendWireMock.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") {
                "http://localhost:${jwksWireMock.port()}/.well-known/jwks.json"
            }
            registry.add("gateway.hmac-secret") { "integration-test-hmac-secret" }

            // Route with SUBSCRIBER required role
            registry.add("gateway.routes[0].path-prefix") { "/api/config" }
            registry.add("gateway.routes[0].target") { "http://localhost:${backendWireMock.port()}" }
            registry.add("gateway.routes[0].public") { "false" }
            registry.add("gateway.routes[0].strip-prefix") { "false" }
            registry.add("gateway.routes[0].required-roles[0]") { "SUBSCRIBER" }

            // Route without required roles
            registry.add("gateway.routes[1].path-prefix") { "/api/users" }
            registry.add("gateway.routes[1].target") { "http://localhost:${backendWireMock.port()}" }
            registry.add("gateway.routes[1].public") { "false" }
            registry.add("gateway.routes[1].strip-prefix") { "false" }

            // Large rate limit so rate limiting does not interfere
            registry.add("gateway.rate-limit.capacity") { "1000" }
            registry.add("gateway.rate-limit.overdraft") { "100" }
        }

        fun generateJwt(
            sub: String = UUID.randomUUID().toString(),
            roles: List<String> = listOf("USER"),
            subscriptionTier: String = "free",
            jti: String = UUID.randomUUID().toString()
        ): String {
            val claims = JWTClaimsSet.Builder()
                .subject(sub)
                .claim("roles", roles)
                .claim("subscription_tier", subscriptionTier)
                .jwtID(jti)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build()

            val header = JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID("test-key-id")
                .build()

            val signedJwt = SignedJWT(header, claims)
            signedJwt.sign(signer)
            return signedJwt.serialize()
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var redisTemplate: ReactiveStringRedisTemplate

    @BeforeEach
    fun resetState() {
        backendWireMock.resetAll()
        backendWireMock.stubFor(
            get(urlPathEqualTo("/api/config/sources"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"sources":[]}""")
                )
        )
        backendWireMock.stubFor(
            get(urlPathEqualTo("/api/users/me"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"id":"user-1"}""")
                )
        )
        redisTemplate.connectionFactory?.reactiveConnection
            ?.serverCommands()?.flushAll()?.block()
    }

    @Test
    fun `should return 403 when free user accesses SUBSCRIBER-required route`() {
        val token = generateJwt(subscriptionTier = "free")

        webTestClient.get()
            .uri("/api/config/sources")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.error").isEqualTo("subscription_required")
            .jsonPath("$.request_id").isNotEmpty
    }

    @Test
    fun `should allow premium user to access SUBSCRIBER-required route`() {
        val token = generateJwt(subscriptionTier = "premium")

        webTestClient.get()
            .uri("/api/config/sources")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `should allow free user to access route without required roles`() {
        val token = generateJwt(subscriptionTier = "free")

        webTestClient.get()
            .uri("/api/users/me")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
    }
}
