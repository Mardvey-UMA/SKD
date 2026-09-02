package com.contentplatform.user.integration.api

import com.contentplatform.user.api.dto.OnboardingRequest
import com.contentplatform.user.api.dto.OnboardingResponse
import com.contentplatform.user.api.dto.ProfileResponse
import com.contentplatform.user.api.dto.UpdateProfileRequest
import com.contentplatform.user.integration.IntegrationTestBase
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get as wireMockGet
import com.github.tomakehurst.wiremock.client.WireMock.post as wireMockPost
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Duration
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserApiIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    private val objectMapper = ObjectMapper()
    private val hmacSecret = "test-hmac-secret-for-tests"

    companion object {
        private val wireMock = WireMockServer(wireMockConfig().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun startWireMock() {
            wireMock.start()
        }

        @AfterAll
        @JvmStatic
        fun stopWireMock() {
            wireMock.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun recSystemProperties(registry: DynamicPropertyRegistry) {
            registry.add("services.rec-system.url") { "http://localhost:${wireMock.port()}" }
            registry.add("services.rec-system.retry-delay-ms") { "100" }
        }
    }

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM outbox")
        jdbcTemplate.execute("DELETE FROM profiles")
        wireMock.resetAll()
    }

    private fun computeHmac(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray()))
    }

    private fun authenticatedHeaders(method: String, path: String, userId: UUID): HttpHeaders {
        val roles = "USER"
        val tier = "free"
        val requestId = UUID.randomUUID().toString()
        return HttpHeaders().apply {
            set("X-Gateway-Signature", computeHmac("$userId|$roles|$tier|$requestId"))
            set("X-User-Id", userId.toString())
            set("X-User-Roles", roles)
            set("X-Subscription-Tier", tier)
            set("X-Request-Id", requestId)
            contentType = MediaType.APPLICATION_JSON
        }
    }

    private fun createProfileViaKafka(userId: UUID, email: String) {
        val message = """
            {
              "event_type": "user.registered",
              "aggregate_type": "User",
              "aggregate_id": "$userId",
              "payload": {
                "user_id": "$userId",
                "email": "$email",
                "timestamp": "2026-04-05T10:00:00Z"
              }
            }
        """.trimIndent()

        kafkaTemplate.send("user.registered", userId.toString(), message).get()

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM profiles WHERE id = ?",
                Int::class.java,
                userId
            )
            assertThat(count).isEqualTo(1)
        }
    }

    @Nested
    inner class `GET api users me` {

        @Test
        fun `should return profile data after Kafka consumer creates profile`() {
            val userId = UUID.randomUUID()
            val email = "api-test@example.com"
            createProfileViaKafka(userId, email)

            val path = "/api/users/me"
            val headers = authenticatedHeaders("GET", path, userId)
            val entity = HttpEntity<Void>(headers)

            val response = restTemplate.exchange(path, HttpMethod.GET, entity, String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body).contains(userId.toString())
            assertThat(response.body).contains(email)
            assertThat(response.body).contains("\"subscription_tier\"")
            assertThat(response.body).contains("\"onboarding_completed\"")
        }

        @Test
        fun `should return 401 without HMAC headers`() {
            val response = restTemplate.getForEntity("/api/users/me", String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `should return 404 when profile does not exist`() {
            val userId = UUID.randomUUID()
            val path = "/api/users/me"
            val headers = authenticatedHeaders("GET", path, userId)
            val entity = HttpEntity<Void>(headers)

            val response = restTemplate.exchange(path, HttpMethod.GET, entity, String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
            assertThat(response.body).contains("NOT_FOUND")
        }
    }

    @Nested
    inner class `PUT api users me` {

        @Test
        fun `should update profile and return updated data`() {
            val userId = UUID.randomUUID()
            createProfileViaKafka(userId, "update-test@example.com")

            val path = "/api/users/me"
            val headers = authenticatedHeaders("PUT", path, userId)
            val request = UpdateProfileRequest(
                displayName = "Updated Name",
                avatarUrl = "https://cdn.example.com/new-avatar.jpg"
            )
            val entity = HttpEntity(objectMapper.writeValueAsString(request), headers)

            val response = restTemplate.exchange(path, HttpMethod.PUT, entity, String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body).contains("Updated Name")
            assertThat(response.body).contains("new-avatar.jpg")
        }
    }

    @Nested
    inner class `POST api users me onboarding` {

        @Test
        fun `should complete onboarding with valid categories and return success`() {
            val userId = UUID.randomUUID()
            createProfileViaKafka(userId, "onboarding-test@example.com")

            wireMock.stubFor(
                wireMockPost(urlEqualTo("/onboarding"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"user_id":"$userId","profile_initialized":true}""")
                    )
            )

            val path = "/api/users/me/onboarding"
            val headers = authenticatedHeaders("POST", path, userId)
            val request = OnboardingRequest(
                categories = listOf("technology", "science", "business"),
                sourceContentIds = emptyList()
            )
            val entity = HttpEntity(objectMapper.writeValueAsString(request), headers)

            val response = restTemplate.exchange(path, HttpMethod.POST, entity, String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body).contains("\"onboarding_completed\"")
            assertThat(response.body).contains("true")

            // Verify profile was updated in DB
            val onboardingCompleted = jdbcTemplate.queryForObject(
                "SELECT onboarding_completed FROM profiles WHERE id = ?",
                Boolean::class.java,
                userId
            )
            assertThat(onboardingCompleted).isTrue()
        }
    }

    @Nested
    inner class `GET api users me categories` {

        @Test
        fun `should proxy categories from rec-system`() {
            val userId = UUID.randomUUID()
            createProfileViaKafka(userId, "categories-test@example.com")

            val categoriesResponse = """{"categories":[{"id":"technology","name":"Technology","icon":"laptop"},{"id":"science","name":"Science","icon":"flask"}],"min_select":3,"max_select":5}"""
            wireMock.stubFor(
                wireMockGet(urlEqualTo("/categories?locale=ru"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(categoriesResponse)
                    )
            )

            val path = "/api/users/me/categories"
            val headers = authenticatedHeaders("GET", path, userId)
            val entity = HttpEntity<Void>(headers)

            val response = restTemplate.exchange(path, HttpMethod.GET, entity, String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body).contains("technology")
            assertThat(response.body).contains("min_select")
        }
    }
}
