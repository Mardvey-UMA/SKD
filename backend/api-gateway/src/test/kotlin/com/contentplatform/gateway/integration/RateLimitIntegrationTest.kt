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
import org.assertj.core.api.Assertions.assertThat
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
 * Integration tests for rate limiting with real Redis via Testcontainers.
 *
 * Verifies:
 * - Sending requests up to the limit succeeds
 * - Exceeding the limit returns 429 with Retry-After header
 * - Different users have independent rate limits
 * - Response body contains rate_limit_exceeded error in JSON format
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RateLimitIntegrationTest {

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

            // Protected route -> backend WireMock
            registry.add("gateway.routes[0].path-prefix") { "/api/users" }
            registry.add("gateway.routes[0].target") { "http://localhost:${backendWireMock.port()}" }
            registry.add("gateway.routes[0].public") { "false" }
            registry.add("gateway.routes[0].strip-prefix") { "false" }

            // Use small rate limit for testing
            registry.add("gateway.rate-limit.capacity") { "5" }
            registry.add("gateway.rate-limit.window-seconds") { "60" }
            registry.add("gateway.rate-limit.overdraft") { "2" }
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
            get(urlPathEqualTo("/api/users/me"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"id":"user-1","name":"Test User"}""")
                )
        )
        // Flush Redis to reset rate counters
        redisTemplate.connectionFactory?.reactiveConnection
            ?.serverCommands()?.flushAll()?.block()
    }

    @Test
    fun `should allow requests within rate limit`() {
        val userId = "rate-limit-user-${UUID.randomUUID()}"
        val token = generateJwt(sub = userId)

        // Send 5 requests (within capacity)
        repeat(5) {
            webTestClient.get()
                .uri("/api/users/me")
                .header("Authorization", "Bearer $token")
                .exchange()
                .expectStatus().isOk
        }
    }

    @Test
    fun `should return 429 when rate limit exceeded`() {
        val userId = "rate-exceed-user-${UUID.randomUUID()}"
        val token = generateJwt(sub = userId)

        // Send capacity + overdraft requests (5 + 2 = 7 should succeed)
        repeat(7) { i ->
            webTestClient.get()
                .uri("/api/users/me")
                .header("Authorization", "Bearer $token")
                .exchange()
                .expectStatus().isOk
        }

        // The 8th request should be rate limited
        webTestClient.get()
            .uri("/api/users/me")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectHeader().exists("Retry-After")
    }

    @Test
    fun `should have independent rate limits per user`() {
        val user1 = "user-a-${UUID.randomUUID()}"
        val user2 = "user-b-${UUID.randomUUID()}"
        val token1 = generateJwt(sub = user1)
        val token2 = generateJwt(sub = user2)

        // Exhaust user1's limit (7 = capacity + overdraft)
        repeat(7) {
            webTestClient.get()
                .uri("/api/users/me")
                .header("Authorization", "Bearer $token1")
                .exchange()
                .expectStatus().isOk
        }

        // user1 should be rate limited
        webTestClient.get()
            .uri("/api/users/me")
            .header("Authorization", "Bearer $token1")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)

        // user2 should still be allowed
        webTestClient.get()
            .uri("/api/users/me")
            .header("Authorization", "Bearer $token2")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `should return JSON error body when rate limited`() {
        val userId = "rate-json-user-${UUID.randomUUID()}"
        val token = generateJwt(sub = userId)

        // Exhaust limit
        repeat(7) {
            webTestClient.get()
                .uri("/api/users/me")
                .header("Authorization", "Bearer $token")
                .exchange()
        }

        // 8th request should return JSON error
        webTestClient.get()
            .uri("/api/users/me")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            .expectHeader().contentType("application/json")
            .expectBody()
            .jsonPath("$.error").isEqualTo("rate_limit_exceeded")
            .jsonPath("$.request_id").isNotEmpty
    }
}
