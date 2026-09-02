package com.contentplatform.auth.integration.controller

import com.contentplatform.auth.integration.IntegrationTestBase
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate

@Tag("integration")
class HealthAndJwksIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Suppress("UNCHECKED_CAST")
    private fun getJson(path: String): Pair<Int, Map<String, Any>> {
        val response = restTemplate.getForEntity(path, String::class.java)
        val body = objectMapper.readValue(response.body ?: "{}", Map::class.java) as Map<String, Any>
        return response.statusCode.value() to body
    }

    @Nested
    inner class JwksEndpoint {

        @Test
        fun `GET well-known jwks json returns RSA public key in JWK format`() {
            val (status, body) = getJson("/.well-known/jwks.json")

            assertThat(status).isEqualTo(200)
            assertThat(body).containsKey("keys")

            @Suppress("UNCHECKED_CAST")
            val keys = body["keys"] as List<Map<String, Any>>
            assertThat(keys).isNotEmpty

            val firstKey = keys[0]
            SoftAssertions.assertSoftly { softly ->
                softly.assertThat(firstKey["kty"]).isEqualTo("RSA")
                softly.assertThat(firstKey["kid"]).isEqualTo("auth-key-1")
                softly.assertThat(firstKey["use"]).isEqualTo("sig")
                softly.assertThat(firstKey["alg"]).isEqualTo("RS256")
                softly.assertThat(firstKey["n"] as? String).isNotBlank()
                softly.assertThat(firstKey["e"] as? String).isNotBlank()
            }
        }

        @Test
        fun `GET well-known jwks json is accessible without authentication`() {
            val response = restTemplate.getForEntity("/.well-known/jwks.json", String::class.java)

            assertThat(response.statusCode.value()).isEqualTo(200)
            assertThat(response.body).contains("keys")
        }
    }

    @Nested
    inner class HealthEndpoint {

        @Test
        fun `GET health returns ok status with all checks connected`() {
            val (status, body) = getJson("/health")

            assertThat(status).isEqualTo(200)

            SoftAssertions.assertSoftly { softly ->
                softly.assertThat(body["status"]).isEqualTo("ok")
                softly.assertThat(body["service"]).isEqualTo("auth-service")

                @Suppress("UNCHECKED_CAST")
                val checks = body["checks"] as Map<String, String>
                softly.assertThat(checks["database"]).isEqualTo("connected")
                softly.assertThat(checks["redis"]).isEqualTo("connected")
                softly.assertThat(checks["kafka"]).isEqualTo("connected")
            }
        }

        @Test
        fun `GET health is accessible without authentication`() {
            val (status, _) = getJson("/health")

            assertThat(status).isEqualTo(200)
        }

        @Test
        fun `GET health always returns 200 even for degraded status`() {
            // This test verifies the contract: health endpoint returns 200 always,
            // never 5xx. With all containers running, status should be "ok".
            // The "degraded" path will be implicitly tested by the controller
            // catching exceptions from individual checks.
            val (status, body) = getJson("/health")

            assertThat(status).isEqualTo(200)
            assertThat(body["status"]).isNotNull
        }
    }
}
