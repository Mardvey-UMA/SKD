package com.contentplatform.gateway.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Full pipeline integration tests exercising the complete filter chain:
 *
 *   CorsWebFilter -> Spring Security -> TokenRevocationFilter
 *   -> HeaderEnrichmentFilter -> RateLimitFilter -> SubscriptionGateFilter -> ProxyFilter
 *
 * Uses:
 * - Testcontainers Redis (token revocation, rate limiting)
 * - WireMock for JWKS endpoint (RSA public key)
 * - WireMock as backend services to capture forwarded requests and return responses
 * - nimbus-jose-jwt for RSA key pair generation and JWT signing
 *
 * Each test exercises the ENTIRE filter chain end-to-end, not individual filters.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayPipelineIntegrationTest {

    companion object {
        private const val HMAC_SECRET = "pipeline-e2e-hmac-secret-key-32chars!"

        // RSA key pair for signing valid JWTs
        private val rsaKey: RSAKey = RSAKeyGenerator(2048)
            .keyID("pipeline-test-key")
            .generate()
        private val jwksJson: String = JWKSet(rsaKey.toPublicJWK()).toString()
        private val signer = RSASSASigner(rsaKey)

        // Separate RSA key for invalid-signature JWTs
        private val wrongRsaKey: RSAKey = RSAKeyGenerator(2048)
            .keyID("wrong-pipeline-key")
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
        fun startInfrastructure() {
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
        fun stopInfrastructure() {
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

            // Public route (no auth required)
            registry.add("gateway.routes[0].path-prefix") { "/api/auth" }
            registry.add("gateway.routes[0].target") { "http://localhost:${backendWireMock.port()}" }
            registry.add("gateway.routes[0].public") { "true" }
            registry.add("gateway.routes[0].strip-prefix") { "false" }

            // Protected route (auth required, no special roles)
            registry.add("gateway.routes[1].path-prefix") { "/api/users" }
            registry.add("gateway.routes[1].target") { "http://localhost:${backendWireMock.port()}" }
            registry.add("gateway.routes[1].public") { "false" }
            registry.add("gateway.routes[1].strip-prefix") { "false" }

            // Protected route with SUBSCRIBER role requirement
            registry.add("gateway.routes[2].path-prefix") { "/api/config" }
            registry.add("gateway.routes[2].target") { "http://localhost:${backendWireMock.port()}" }
            registry.add("gateway.routes[2].public") { "false" }
            registry.add("gateway.routes[2].strip-prefix") { "false" }
            registry.add("gateway.routes[2].required-roles[0]") { "SUBSCRIBER" }

            // Dead route (nothing listens) for 502 testing
            registry.add("gateway.routes[3].path-prefix") { "/api/dead" }
            registry.add("gateway.routes[3].target") { "http://localhost:1" }
            registry.add("gateway.routes[3].public") { "true" }
            registry.add("gateway.routes[3].strip-prefix") { "false" }

            // Rate limit: small values for testing (10 capacity + 2 overdraft = 12 total before 429)
            registry.add("gateway.rate-limit.capacity") { "10" }
            registry.add("gateway.rate-limit.window-seconds") { "60" }
            registry.add("gateway.rate-limit.overdraft") { "2" }

            // CORS origins for testing
            registry.add("gateway.cors.allowed-origins[0]") { "http://localhost:3000" }
            registry.add("gateway.cors.allowed-origins[1]") { "http://localhost:5173" }
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
                .keyID(if (useWrongKey) "wrong-pipeline-key" else "pipeline-test-key")
                .build()

            val signedJwt = SignedJWT(header, claims)
            signedJwt.sign(if (useWrongKey) wrongSigner else signer)
            return signedJwt.serialize()
        }

        fun computeExpectedHmac(payload: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(HMAC_SECRET.toByteArray(), "HmacSHA256"))
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var redisTemplate: ReactiveStringRedisTemplate

    @BeforeEach
    fun resetState() {
        backendWireMock.resetAll()

        // Stub backend responses for all protected and public routes
        backendWireMock.stubFor(
            get(urlPathEqualTo("/api/users/me"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"id":"user-1","name":"Pipeline Test User"}""")
                )
        )
        backendWireMock.stubFor(
            post(urlPathEqualTo("/api/auth/login"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token":"jwt-token-here","token_type":"Bearer"}""")
                )
        )
        backendWireMock.stubFor(
            get(urlPathEqualTo("/api/config/sources"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"sources":["telegram","rss"]}""")
                )
        )

        // Flush Redis to reset rate limits and revocation entries
        redisTemplate.connectionFactory?.reactiveConnection
            ?.serverCommands()?.flushAll()?.block()
    }

    // ===== SCENARIO 1: Happy path authenticated request =====

    @Nested
    @DisplayName("Happy path: authenticated request through full pipeline")
    inner class HappyPathAuthenticated {

        @Test
        fun `valid JWT should pass all filters and reach backend with correct response`() {
            val userId = "e2e-user-${UUID.randomUUID()}"
            val token = generateJwt(sub = userId, roles = listOf("USER"), subscriptionTier = "free")

            webTestClient.get()
                .uri("/api/users/me")
                .header("Authorization", "Bearer $token")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .value { body ->
                    assertThat(body).contains("Pipeline Test User")
                }

            backendWireMock.verify(getRequestedFor(urlPathEqualTo("/api/users/me")))
        }

        @Test
        fun `forwarded request should contain all enrichment headers`() {
            val userId = "header-check-user-${UUID.randomUUID()}"
            val roles = listOf("USER", "ADMIN")
            val tier = "premium"
            val token = generateJwt(sub = userId, roles = roles, subscriptionTier = tier)

            webTestClient.get()
                .uri("/api/users/me")
                .header("Authorization", "Bearer $token")
                .exchange()
                .expectStatus().isOk

            val requests = backendWireMock.findAll(getRequestedFor(urlPathEqualTo("/api/users/me")))
            assertThat(requests).hasSize(1)

            val forwarded = requests[0]

            // X-User-Id from JWT sub claim
            assertThat(forwarded.getHeader("X-User-Id")).isEqualTo(userId)

            // X-User-Roles comma-joined
            assertThat(forwarded.getHeader("X-User-Roles")).isEqualTo("USER,ADMIN")

            // X-Subscription-Tier from JWT claim
            assertThat(forwarded.getHeader("X-Subscription-Tier")).isEqualTo(tier)

            // X-Request-Id should be a valid UUID
            val requestId = forwarded.getHeader("X-Request-Id")
            assertThat(requestId).isNotNull()
            assertThat(UUID.fromString(requestId)).isNotNull()

            // X-Gateway-Signature should be a valid HMAC-SHA256
            val signature = forwarded.getHeader("X-Gateway-Signature")
            assertThat(signature).isNotNull()
            assertThat(signature).isNotBlank()

            // Verify HMAC is correct
            val expectedPayload = "$userId|USER,ADMIN|$tier|$requestId"
            val expectedSignature = computeExpectedHmac(expectedPayload)
            assertThat(signature).isEqualTo(expectedSignature)
        }

        @Test
        fun `backend response body and status should be returned to client`() {
            val token = generateJwt()

            webTestClient.get()
                .uri("/api/users/me")
                .header("Authorization", "Bearer $token")
                .exchange()
                .expectStatus().isOk
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.id").isEqualTo("user-1")
                .jsonPath("$.name").isEqualTo("Pipeline Test User")
        }
    }

    // ===== SCENARIO 2: Happy path public request =====

    @Nested
    @DisplayName("Happy path: public request (no JWT)")
    inner class HappyPathPublic {

        @Test
        fun `POST to public auth route should forward without JWT`() {
            webTestClient.post()
                .uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .bodyValue("""{"email":"user@example.com","password":"secret123"}""")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .value { body ->
                    assertThat(body).contains("jwt-token-here")
                }

            backendWireMock.verify(postRequestedFor(urlPathEqualTo("/api/auth/login")))
        }
    }

    // ===== SCENARIO 3: Token revocation blocks request =====

    @Nested
    @DisplayName("Token revocation: revoked jti in Redis should block request")
    inner class TokenRevocation {

        @Test
        fun `should return 401 when JWT jti is in Redis revocation set`() {
            val jti = "revoked-pipeline-jti-${UUID.randomUUID()}"
            val token = generateJwt(jti = jti)

            // Place revocation entry in Redis
            redisTemplate.opsForValue()
                .set("revoked:$jti", "1", Duration.ofMinutes(15))
                .block()

            webTestClient.get()
                .uri("/api/users/me")
                .header("Authorization", "Bearer $token")
                .exchange()
                .expectStatus().isUnauthorized
                .expectBody()
                .jsonPath("$.error").isEqualTo("token_revoked")
        }
    }

    // ===== SCENARIO 4: Rate limit exceeded =====

    @Nested
    @DisplayName("Rate limiting: exceeding capacity+overdraft should return 429")
    inner class RateLimitExceeded {

        @Test
        fun `should return 429 with Retry-After after exceeding rate limit`() {
            val userId = "rate-pipeline-user-${UUID.randomUUID()}"
            val token = generateJwt(sub = userId)

            // Send capacity + overdraft requests (10 + 2 = 12 should succeed)
            repeat(12) {
                webTestClient.get()
                    .uri("/api/users/me")
                    .header("Authorization", "Bearer $token")
                    .exchange()
                    .expectStatus().isOk
            }

            // The 13th request should be rate limited
            webTestClient.get()
                .uri("/api/users/me")
                .header("Authorization", "Bearer $token")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().exists("Retry-After")
                .expectBody()
                .jsonPath("$.error").isEqualTo("rate_limit_exceeded")
        }
    }

    // ===== SCENARIO 5: Subscription gate blocks free user =====

    @Nested
    @DisplayName("Subscription gate: free user on SUBSCRIBER route gets 403")
    inner class SubscriptionGateBlock {

        @Test
        fun `should return 403 subscription_required for free user on config route`() {
            val token = generateJwt(subscriptionTier = "free")

            webTestClient.get()
                .uri("/api/config/sources")
                .header("Authorization", "Bearer $token")
                .exchange()
                .expectStatus().isForbidden
                .expectBody()
                .jsonPath("$.error").isEqualTo("subscription_required")
        }
    }

    // ===== SCENARIO 6: Subscription gate allows premium user =====

    @Nested
    @DisplayName("Subscription gate: premium user on SUBSCRIBER route passes through")
    inner class SubscriptionGateAllow {

        @Test
        fun `should forward request for premium user on config route`() {
            val token = generateJwt(subscriptionTier = "premium")

            webTestClient.get()
                .uri("/api/config/sources")
                .header("Authorization", "Bearer $token")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .value { body ->
                    assertThat(body).contains("telegram")
                }

            backendWireMock.verify(getRequestedFor(urlPathEqualTo("/api/config/sources")))
        }
    }

    // ===== SCENARIO 7: CORS preflight handled =====

    @Nested
    @DisplayName("CORS: preflight OPTIONS request should get CORS headers without auth")
    inner class CorsPreflight {

        @Test
        fun `should return 200 with CORS headers for OPTIONS preflight`() {
            webTestClient.options()
                .uri("/api/users/me")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization")
                .exchange()
                .expectStatus().isOk
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000")
                .expectHeader().exists("Access-Control-Allow-Methods")
                .expectHeader().exists("Access-Control-Max-Age")
        }

        @Test
        fun `should reject CORS preflight from disallowed origin`() {
            webTestClient.options()
                .uri("/api/users/me")
                .header("Origin", "http://evil-site.com")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin")
        }

        @Test
        fun `should reject CORS preflight from localhost 8100 when wildcard flag is false`() {
            // Default config has allow-localhost-wildcard=false, so port 8100 is not an allowed origin
            webTestClient.options()
                .uri("/api/users/me")
                .header("Origin", "http://localhost:8100")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin")
        }
    }

    // ===== SCENARIO 8: Backend 502 =====

    @Nested
    @DisplayName("Backend unreachable: should return 502")
    inner class BackendUnreachable {

        @Test
        fun `should return 502 when backend is not reachable`() {
            // /api/dead route points to localhost:1 where nothing listens
            webTestClient.get()
                .uri("/api/dead/anything")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.error").isEqualTo("service_unavailable")
        }
    }

    // ===== SCENARIO 9: Invalid JWT rejected =====

    @Nested
    @DisplayName("Invalid JWT: tampered token should be rejected")
    inner class InvalidJwt {

        @Test
        fun `should return 401 for JWT signed with wrong key`() {
            val token = generateJwt(useWrongKey = true)

            webTestClient.get()
                .uri("/api/users/me")
                .header("Authorization", "Bearer $token")
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `should return 401 for completely malformed JWT`() {
            webTestClient.get()
                .uri("/api/users/me")
                .header("Authorization", "Bearer not.a.valid.jwt.token")
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `should return 401 for missing Authorization header on protected route`() {
            webTestClient.get()
                .uri("/api/users/me")
                .exchange()
                .expectStatus().isUnauthorized
        }
    }

    // ===== SCENARIO 10: Complete filter chain ordering verification =====

    @Nested
    @DisplayName("Filter chain ordering: all enrichment headers present on forwarded request")
    inner class FilterChainOrdering {

        @Test
        fun `should have all expected headers on forwarded request in correct format`() {
            val userId = "ordering-test-${UUID.randomUUID()}"
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

            val forwarded = requests[0]

            // Verify ALL gateway-enriched headers are present
            assertThat(forwarded.getHeader("X-User-Id")).isEqualTo(userId)
            assertThat(forwarded.getHeader("X-User-Roles")).isEqualTo("USER")
            assertThat(forwarded.getHeader("X-Subscription-Tier")).isEqualTo(tier)

            val requestId = forwarded.getHeader("X-Request-Id")
            assertThat(requestId).isNotNull()
            // Request ID must be a valid UUID
            val parsedUuid = UUID.fromString(requestId)
            assertThat(parsedUuid).isNotNull()

            // HMAC signature must be present and valid Base64
            val signature = forwarded.getHeader("X-Gateway-Signature")
            assertThat(signature).isNotNull()
            val decoded = Base64.getDecoder().decode(signature)
            assertThat(decoded).hasSize(32) // HMAC-SHA256 = 32 bytes

            // HMAC must match the expected payload format
            val expectedPayload = "$userId|USER|$tier|$requestId"
            val expectedSig = computeExpectedHmac(expectedPayload)
            assertThat(signature).isEqualTo(expectedSig)
        }

        @Test
        fun `request ID should be present on response even for errors`() {
            // No JWT on protected route -> 401, but X-Request-Id should still be on the response
            webTestClient.get()
                .uri("/api/users/me")
                .exchange()
                .expectStatus().isUnauthorized
                .expectHeader().exists("X-Request-Id")
        }
    }
}
