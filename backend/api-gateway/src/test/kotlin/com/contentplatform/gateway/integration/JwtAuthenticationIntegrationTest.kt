package com.contentplatform.gateway.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * Integration tests for JWT authentication, token revocation, and header enrichment + HMAC signing.
 *
 * Uses:
 * - Testcontainers Redis for token revocation
 * - WireMock for JWKS endpoint (serves RSA public key)
 * - WireMock as backend service to capture forwarded headers
 * - nimbus-jose-jwt for generating RSA key pair and signing test JWTs
 *
 * Verifies:
 * - Valid JWT -> request forwarded to backend
 * - Expired JWT -> 401
 * - Invalid signature JWT -> 401
 * - No JWT on protected route -> 401
 * - No JWT on public route -> forwarded
 * - Revoked token (Redis key exists) -> 401
 * - Valid JWT -> X-User-Id, X-User-Roles, X-Subscription-Tier, X-Gateway-Signature headers on forwarded request
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JwtAuthenticationIntegrationTest {

    companion object {
        private const val HMAC_SECRET = "integration-test-hmac-secret-key"

        // Generate RSA key pair for JWT signing
        private val rsaKey: RSAKey = RSAKeyGenerator(2048)
            .keyID("test-key-id")
            .generate()

        private val jwksJson: String = JWKSet(rsaKey.toPublicJWK()).toString()
        private val signer = RSASSASigner(rsaKey)

        // A separate RSA key for generating invalid-signature tokens
        private val wrongRsaKey: RSAKey = RSAKeyGenerator(2048)
            .keyID("wrong-key-id")
            .generate()
        private val wrongSigner = RSASSASigner(wrongRsaKey)

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

            // Serve JWKS with the RSA public key
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
            registry.add("gateway.hmac-secret") { HMAC_SECRET }

            // Public route -> backend WireMock
            registry.add("gateway.routes[0].path-prefix") { "/api/auth" }
            registry.add("gateway.routes[0].target") { "http://localhost:${backendWireMock.port()}" }
            registry.add("gateway.routes[0].public") { "true" }
            registry.add("gateway.routes[0].strip-prefix") { "false" }

            // Protected route -> backend WireMock
            registry.add("gateway.routes[1].path-prefix") { "/api/users" }
            registry.add("gateway.routes[1].target") { "http://localhost:${backendWireMock.port()}" }
            registry.add("gateway.routes[1].public") { "false" }
            registry.add("gateway.routes[1].strip-prefix") { "false" }
        }

        fun generateJwt(
            sub: String = UUID.randomUUID().toString(),
            roles: List<String> = listOf("USER"),
            subscriptionTier: String = "free",
            jti: String = UUID.randomUUID().toString(),
            expiresAt: Date = Date.from(Instant.now().plusSeconds(3600)),
            issuedAt: Date = Date.from(Instant.now()),
            useWrongKey: Boolean = false
        ): String {
            val claims = JWTClaimsSet.Builder()
                .subject(sub)
                .claim("roles", roles)
                .claim("subscription_tier", subscriptionTier)
                .jwtID(jti)
                .issueTime(issuedAt)
                .expirationTime(expiresAt)
                .build()

            val header = JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(if (useWrongKey) "wrong-key-id" else "test-key-id")
                .build()

            val signedJwt = SignedJWT(header, claims)
            signedJwt.sign(if (useWrongKey) wrongSigner else signer)
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
        // Stub a default backend response
        backendWireMock.stubFor(
            get(urlPathEqualTo("/api/users/me"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"id":"user-1","name":"Test User"}""")
                )
        )
        backendWireMock.stubFor(
            get(urlPathEqualTo("/api/auth/register"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("""{"ok":true}""")
                )
        )
        // Flush Redis
        redisTemplate.connectionFactory?.reactiveConnection
            ?.serverCommands()?.flushAll()?.block()
    }

    // --- JWT Validation Tests ---

    @Test
    fun `should forward request with valid JWT to backend`() {
        val token = generateJwt(sub = "user-123")

        webTestClient.get()
            .uri("/api/users/me")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { body ->
                assertThat(body).contains("Test User")
            }

        backendWireMock.verify(getRequestedFor(urlPathEqualTo("/api/users/me")))
    }

    @Test
    fun `should return 401 for expired JWT`() {
        val token = generateJwt(
            expiresAt = Date.from(Instant.now().minusSeconds(3600)),
            issuedAt = Date.from(Instant.now().minusSeconds(7200))
        )

        webTestClient.get()
            .uri("/api/users/me")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `should return 401 for JWT with invalid signature`() {
        val token = generateJwt(useWrongKey = true)

        webTestClient.get()
            .uri("/api/users/me")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `should return 401 when no JWT on protected route`() {
        webTestClient.get()
            .uri("/api/users/me")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `should forward request on public route without JWT`() {
        webTestClient.get()
            .uri("/api/auth/register")
            .exchange()
            .expectStatus().isOk

        backendWireMock.verify(getRequestedFor(urlPathEqualTo("/api/auth/register")))
    }

    // --- Token Revocation Tests ---

    @Test
    fun `should return 401 when token is revoked in Redis`() {
        val jti = "revoked-token-jti"
        val token = generateJwt(jti = jti)

        // Add revocation entry to Redis
        redisTemplate.opsForValue()
            .set("revoked:$jti", "1", Duration.ofMinutes(15))
            .block()

        webTestClient.get()
            .uri("/api/users/me")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `should forward request when token is not revoked`() {
        val jti = "not-revoked-jti"
        val token = generateJwt(jti = jti)

        // No Redis entry for this jti

        webTestClient.get()
            .uri("/api/users/me")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
    }

    // --- Header Enrichment + HMAC Tests ---

    @Test
    fun `should forward X-User-Id header from JWT sub claim`() {
        val userId = "550e8400-e29b-41d4-a716-446655440000"
        val token = generateJwt(sub = userId)

        webTestClient.get()
            .uri("/api/users/me")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk

        // Verify the backend received the X-User-Id header
        val requests = backendWireMock.findAll(getRequestedFor(urlPathEqualTo("/api/users/me")))
        assertThat(requests).hasSize(1)
        assertThat(requests[0].getHeader("X-User-Id")).isEqualTo(userId)
    }

    @Test
    fun `should forward X-User-Roles header as comma-joined roles`() {
        val token = generateJwt(roles = listOf("USER", "SUBSCRIBER"))

        webTestClient.get()
            .uri("/api/users/me")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk

        val requests = backendWireMock.findAll(getRequestedFor(urlPathEqualTo("/api/users/me")))
        assertThat(requests).hasSize(1)
        assertThat(requests[0].getHeader("X-User-Roles")).isEqualTo("USER,SUBSCRIBER")
    }

    @Test
    fun `should forward X-Subscription-Tier header from JWT claim`() {
        val token = generateJwt(subscriptionTier = "premium")

        webTestClient.get()
            .uri("/api/users/me")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk

        val requests = backendWireMock.findAll(getRequestedFor(urlPathEqualTo("/api/users/me")))
        assertThat(requests).hasSize(1)
        assertThat(requests[0].getHeader("X-Subscription-Tier")).isEqualTo("premium")
    }

    @Test
    fun `should forward X-Gateway-Signature header as valid HMAC-SHA256`() {
        val userId = "user-hmac-test"
        val roles = listOf("USER")
        val tier = "free"
        val token = generateJwt(sub = userId, roles = roles, subscriptionTier = tier)

        webTestClient.get()
            .uri("/api/users/me")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk

        val requests = backendWireMock.findAll(getRequestedFor(urlPathEqualTo("/api/users/me")))
        assertThat(requests).hasSize(1)

        val signature = requests[0].getHeader("X-Gateway-Signature")
        assertThat(signature).isNotNull()
        assertThat(signature).isNotBlank()

        // Verify signature is valid Base64
        val decoded = java.util.Base64.getDecoder().decode(signature)
        assertThat(decoded).hasSize(32) // HMAC-SHA256 = 32 bytes

        // Verify signature matches expected HMAC computation
        val requestId = requests[0].getHeader("X-Request-Id")
        assertThat(requestId).isNotNull()
        val payload = "$userId|${roles.joinToString(",")}|$tier|$requestId"
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(HMAC_SECRET.toByteArray(), "HmacSHA256"))
        val expectedSignature = java.util.Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
        assertThat(signature).isEqualTo(expectedSignature)
    }

    @Test
    fun `should include X-Request-Id header on forwarded request`() {
        val token = generateJwt()

        webTestClient.get()
            .uri("/api/users/me")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk

        val requests = backendWireMock.findAll(getRequestedFor(urlPathEqualTo("/api/users/me")))
        assertThat(requests).hasSize(1)

        val requestId = requests[0].getHeader("X-Request-Id")
        assertThat(requestId).isNotNull()
        // Should be a valid UUID
        assertThat(UUID.fromString(requestId)).isNotNull()
    }
}
