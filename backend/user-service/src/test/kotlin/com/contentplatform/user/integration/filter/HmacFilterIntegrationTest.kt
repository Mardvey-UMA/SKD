package com.contentplatform.user.integration.filter

import com.contentplatform.user.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("integration")
class HmacFilterIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    private val hmacSecret = "test-hmac-secret-for-tests"

    private fun computeHmac(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray()))
    }

    @Test
    fun `GET health should return 200 without any HMAC headers`() {
        val response = restTemplate.getForEntity("/health", String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun `GET api users me with valid HMAC should pass filter and return non-401 status`() {
        val userId = UUID.randomUUID().toString()
        val roles = "USER"
        val tier = "free"
        val requestId = UUID.randomUUID().toString()
        val signature = computeHmac("$userId|$roles|$tier|$requestId")

        val headers = HttpHeaders().apply {
            set("X-Gateway-Signature", signature)
            set("X-User-Id", userId)
            set("X-User-Roles", roles)
            set("X-Subscription-Tier", tier)
            set("X-Request-Id", requestId)
        }
        val entity = HttpEntity<Void>(headers)

        val response = restTemplate.exchange("/api/users/me", HttpMethod.GET, entity, String::class.java)

        // Filter passes but profile doesn't exist -- we expect something other than 401
        assertThat(response.statusCode).isNotEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `GET api users me without X-Gateway-Signature should return 401`() {
        val headers = HttpHeaders().apply {
            set("X-User-Id", UUID.randomUUID().toString())
        }
        val entity = HttpEntity<Void>(headers)

        val response = restTemplate.exchange("/api/users/me", HttpMethod.GET, entity, String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `GET api users me with wrong signature should return 401`() {
        val headers = HttpHeaders().apply {
            set("X-Gateway-Signature", "definitely-wrong-signature")
            set("X-User-Id", UUID.randomUUID().toString())
        }
        val entity = HttpEntity<Void>(headers)

        val response = restTemplate.exchange("/api/users/me", HttpMethod.GET, entity, String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `GET api users me with signature but missing X-User-Id should return 401`() {
        val headers = HttpHeaders().apply {
            set("X-Gateway-Signature", "some-signature")
        }
        val entity = HttpEntity<Void>(headers)

        val response = restTemplate.exchange("/api/users/me", HttpMethod.GET, entity, String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `401 response body should contain JSON error structure`() {
        val headers = HttpHeaders().apply {
            set("X-User-Id", UUID.randomUUID().toString())
        }
        val entity = HttpEntity<Void>(headers)

        val response = restTemplate.exchange("/api/users/me", HttpMethod.GET, entity, String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(response.body).contains("UNAUTHORIZED")
        assertThat(response.body).contains("message")
    }
}
