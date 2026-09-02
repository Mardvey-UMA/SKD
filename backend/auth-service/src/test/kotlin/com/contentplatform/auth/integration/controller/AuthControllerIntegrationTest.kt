package com.contentplatform.auth.integration.controller

import com.contentplatform.auth.db.repository.EmailVerificationTokenRepository
import com.contentplatform.auth.db.repository.OutboxRepository
import com.contentplatform.auth.db.repository.PasswordResetTokenRepository
import com.contentplatform.auth.db.repository.RefreshTokenRepository
import com.contentplatform.auth.db.repository.UserRepository
import com.contentplatform.auth.integration.IntegrationTestBase
import com.contentplatform.auth.service.EmailService
import com.ninjasquad.springmockk.MockkBean
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("integration")
class AuthControllerIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var emailVerificationTokenRepository: EmailVerificationTokenRepository

    @Autowired
    lateinit var passwordResetTokenRepository: PasswordResetTokenRepository

    @Autowired
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    lateinit var outboxRepository: OutboxRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @MockkBean(relaxed = true)
    lateinit var emailService: EmailService

    @Value("\${gateway.hmac-secret}")
    lateinit var hmacSecret: String

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM email_verification_tokens")
        jdbcTemplate.execute("DELETE FROM password_reset_tokens")
        jdbcTemplate.execute("DELETE FROM refresh_tokens")
        jdbcTemplate.execute("DELETE FROM outbox")
        jdbcTemplate.execute("DELETE FROM users")
        redisTemplate.keys("verify:*")?.forEach { redisTemplate.delete(it) }
    }

    // --- Helpers ---

    private fun computeHmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    private fun buildAuthenticatedHeaders(
        userId: String = UUID.randomUUID().toString(),
        roles: String = "USER",
        subscriptionTier: String = "free",
        requestId: String = UUID.randomUUID().toString()
    ): HttpHeaders {
        val payload = "$userId|$roles|$subscriptionTier|$requestId"
        val signature = computeHmac(payload)
        return HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-User-Id", userId)
            set("X-User-Roles", roles)
            set("X-Subscription-Tier", subscriptionTier)
            set("X-Request-Id", requestId)
            set("X-Gateway-Signature", signature)
        }
    }

    private fun jsonHeaders(): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

    private fun registerUser(email: String, password: String = "SecurePass123!"): org.springframework.http.ResponseEntity<String> {
        val body = """{"email":"$email","password":"$password"}"""
        return restTemplate.postForEntity("/api/auth/register", HttpEntity(body, jsonHeaders()), String::class.java)
    }

    /**
     * Verifies user using new code-based endpoint.
     * In dev mode (application-test.yml: auth.dev-mode=true) the code is always "000000".
     */
    private fun verifyUserEmail(email: String) {
        val body = """{"email":"$email","code":"000000"}"""
        restTemplate.postForEntity("/api/auth/verify", HttpEntity(body, jsonHeaders()), String::class.java)
    }

    private fun loginUser(email: String, password: String = "SecurePass123!"): org.springframework.http.ResponseEntity<String> {
        val body = """{"email":"$email","password":"$password"}"""
        return restTemplate.postForEntity("/api/auth/login", HttpEntity(body, jsonHeaders()), String::class.java)
    }

    // --- Tests ---

    @Nested
    inner class `POST auth register` {

        @Test
        fun `should return 201 with email for valid registration`() {
            val response = registerUser("newuser@example.com")

            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
            assertThat(response.body).contains("newuser@example.com")
            assertThat(response.body).contains("message")
        }

        @Test
        fun `should return 409 for duplicate email`() {
            registerUser("dup@example.com")
            val response = registerUser("dup@example.com")

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
            assertThat(response.body).contains("error")
        }

        @Test
        fun `should return 422 for too short password`() {
            val body = """{"email":"valid@example.com","password":"Ab1"}"""
            val response = restTemplate.postForEntity("/api/auth/register", HttpEntity(body, jsonHeaders()), String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            assertThat(response.body).contains("error")
        }

        @Test
        fun `should return 422 for invalid email format`() {
            val body = """{"email":"not-an-email","password":"SecurePass123!"}"""
            val response = restTemplate.postForEntity("/api/auth/register", HttpEntity(body, jsonHeaders()), String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            assertThat(response.body).contains("error")
        }

        @Test
        fun `should return 422 for password without uppercase letter`() {
            val body = """{"email":"valid2@example.com","password":"nouppercase123!"}"""
            val response = restTemplate.postForEntity("/api/auth/register", HttpEntity(body, jsonHeaders()), String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        }
    }

    @Nested
    inner class `POST auth verify by code` {

        @Test
        fun `should return 200 for correct 6-digit code in dev mode`() {
            registerUser("verify-code@example.com")

            val body = """{"email":"verify-code@example.com","code":"000000"}"""
            val response = restTemplate.postForEntity("/api/auth/verify", HttpEntity(body, jsonHeaders()), String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body).contains("Email verified")
            assertThat(response.body).contains("verify-code@example.com")
        }

        @Test
        fun `should return 400 INVALID_CODE for wrong code`() {
            registerUser("wrong-code@example.com")

            val body = """{"email":"wrong-code@example.com","code":"999999"}"""
            val response = restTemplate.postForEntity("/api/auth/verify", HttpEntity(body, jsonHeaders()), String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(response.body).contains("INVALID_CODE")
        }

        @Test
        fun `should return 429 TOO_MANY_ATTEMPTS after 5 failed attempts`() {
            registerUser("ratelimit@example.com")

            // Make 5 failed attempts
            repeat(5) {
                val body = """{"email":"ratelimit@example.com","code":"999999"}"""
                restTemplate.postForEntity("/api/auth/verify", HttpEntity(body, jsonHeaders()), String::class.java)
            }

            // 6th attempt should be rate-limited
            val body = """{"email":"ratelimit@example.com","code":"000000"}"""
            val response = restTemplate.postForEntity("/api/auth/verify", HttpEntity(body, jsonHeaders()), String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            assertThat(response.body).contains("TOO_MANY_ATTEMPTS")
            assertThat(response.body).contains("retryAfterSeconds")
        }

        @Test
        fun `should return 400 for code with wrong format (non-digits)`() {
            registerUser("format-check@example.com")

            val body = """{"email":"format-check@example.com","code":"abc123"}"""
            val response = restTemplate.postForEntity("/api/auth/verify", HttpEntity(body, jsonHeaders()), String::class.java)

            assertThat(response.statusCode).isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY)
        }
    }

    @Nested
    inner class `POST auth resend-verification` {

        @Test
        fun `should return 200 for valid unverified user`() {
            registerUser("resend@example.com")

            val body = """{"email":"resend@example.com"}"""
            val response = restTemplate.postForEntity("/api/auth/resend-verification", HttpEntity(body, jsonHeaders()), String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body).contains("message")
        }

        @Test
        fun `should return 429 RESEND_COOLDOWN on second resend within cooldown window`() {
            registerUser("cooldown-test@example.com")

            val body = """{"email":"cooldown-test@example.com"}"""
            // First resend succeeds
            restTemplate.postForEntity("/api/auth/resend-verification", HttpEntity(body, jsonHeaders()), String::class.java)

            // Second resend within 60s cooldown
            val response = restTemplate.postForEntity("/api/auth/resend-verification", HttpEntity(body, jsonHeaders()), String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            assertThat(response.body).contains("RESEND_COOLDOWN")
            assertThat(response.body).contains("retryAfterSeconds")
        }

        @Test
        fun `should return 400 for unknown email`() {
            val body = """{"email":"nobody@example.com"}"""
            val response = restTemplate.postForEntity("/api/auth/resend-verification", HttpEntity(body, jsonHeaders()), String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @Nested
    inner class `GET auth verify (legacy)` {

        @Test
        fun `should return 200 with verified email for valid legacy token`() {
            // Register first (new flow, no DB token created)
            registerUser("legacy-verify@example.com")
            val user = userRepository.findByEmail("legacy-verify@example.com")!!

            // Insert a legacy DB token manually
            jdbcTemplate.update(
                "INSERT INTO email_verification_tokens (token, user_id, expires_at, used) VALUES (?, ?, ?, false)",
                "legacy-test-token", user.id, java.sql.Timestamp.from(Instant.now().plusSeconds(3600))
            )

            val response = restTemplate.getForEntity("/api/auth/verify?token=legacy-test-token", String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body).contains("Email verified successfully")
            assertThat(response.body).contains("legacy-verify@example.com")
        }

        @Test
        fun `should return 400 for already used legacy token`() {
            registerUser("used-token@example.com")
            val user = userRepository.findByEmail("used-token@example.com")!!

            jdbcTemplate.update(
                "INSERT INTO email_verification_tokens (token, user_id, expires_at, used) VALUES (?, ?, ?, false)",
                "reuse-test-token", user.id, java.sql.Timestamp.from(Instant.now().plusSeconds(3600))
            )

            // First verify succeeds
            restTemplate.getForEntity("/api/auth/verify?token=reuse-test-token", String::class.java)
            // Second verify should fail
            val response = restTemplate.getForEntity("/api/auth/verify?token=reuse-test-token", String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(response.body).contains("error")
        }

        @Test
        fun `should return 404 for nonexistent legacy token`() {
            val response = restTemplate.getForEntity("/api/auth/verify?token=nonexistent-token-value", String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
            assertThat(response.body).contains("error")
        }
    }

    @Nested
    inner class `POST auth login` {

        @Test
        fun `should return 200 with tokens for valid credentials`() {
            registerUser("login@example.com")
            verifyUserEmail("login@example.com")

            val response = loginUser("login@example.com")

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            SoftAssertions.assertSoftly { softly ->
                softly.assertThat(response.body).contains("access_token")
                softly.assertThat(response.body).contains("refresh_token")
                softly.assertThat(response.body).contains("token_type")
                softly.assertThat(response.body).contains("Bearer")
                softly.assertThat(response.body).contains("expires_in")
                softly.assertThat(response.body).contains("900")
            }
        }

        @Test
        fun `should return 401 for wrong password`() {
            registerUser("wrong-pass@example.com")
            verifyUserEmail("wrong-pass@example.com")

            val response = loginUser("wrong-pass@example.com", "WrongPassword!")

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            assertThat(response.body).contains("error")
        }

        @Test
        fun `should return 403 for unverified email`() {
            registerUser("unverified@example.com")

            val response = loginUser("unverified@example.com")

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
            assertThat(response.body).contains("error")
        }

        @Test
        fun `should return 401 for unknown email`() {
            val response = loginUser("ghost@example.com")

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            assertThat(response.body).contains("error")
        }
    }

    @Nested
    inner class `POST auth refresh` {

        @Test
        fun `should return 200 with new tokens for valid refresh token`() {
            registerUser("refresh@example.com")
            verifyUserEmail("refresh@example.com")

            val loginResponse = loginUser("refresh@example.com")
            assertThat(loginResponse.statusCode).isEqualTo(HttpStatus.OK)

            val refreshToken = extractJsonField(loginResponse.body!!, "refresh_token")

            val body = """{"refresh_token":"$refreshToken"}"""
            val response = restTemplate.postForEntity("/api/auth/refresh", HttpEntity(body, jsonHeaders()), String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            SoftAssertions.assertSoftly { softly ->
                softly.assertThat(response.body).contains("access_token")
                softly.assertThat(response.body).contains("refresh_token")
                softly.assertThat(response.body).contains("Bearer")
            }
        }

        @Test
        fun `should return 401 for invalid refresh token`() {
            val body = """{"refresh_token":"completely-invalid-token"}"""
            val response = restTemplate.postForEntity("/api/auth/refresh", HttpEntity(body, jsonHeaders()), String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            assertThat(response.body).contains("error")
        }
    }

    @Nested
    inner class `POST auth logout` {

        @Test
        fun `should return 200 for valid HMAC headers and logged-in user`() {
            registerUser("logout@example.com")
            verifyUserEmail("logout@example.com")
            val loginResponse = loginUser("logout@example.com")
            val accessToken = extractJsonField(loginResponse.body!!, "access_token")

            val user = userRepository.findByEmail("logout@example.com")!!
            val headers = buildAuthenticatedHeaders(userId = user.id.toString())
            headers.set("Authorization", "Bearer $accessToken")

            val response = restTemplate.postForEntity("/api/auth/logout", HttpEntity(null, headers), String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body).contains("Logged out successfully")
        }

        @Test
        fun `should return 403 without HMAC headers`() {
            val response = restTemplate.postForEntity(
                "/api/auth/logout",
                HttpEntity(null, jsonHeaders()),
                String::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @Nested
    inner class `POST auth password reset-request` {

        @Test
        fun `should always return 200 with same message regardless of email existence`() {
            val body1 = """{"email":"nobody@example.com"}"""
            val response1 = restTemplate.postForEntity("/api/auth/password/reset-request", HttpEntity(body1, jsonHeaders()), String::class.java)

            assertThat(response1.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response1.body).contains("message")

            registerUser("existing@example.com")
            val body2 = """{"email":"existing@example.com"}"""
            val response2 = restTemplate.postForEntity("/api/auth/password/reset-request", HttpEntity(body2, jsonHeaders()), String::class.java)

            assertThat(response2.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response2.body).contains("message")
        }
    }

    @Nested
    inner class `POST auth password reset` {

        @Test
        fun `should return 200 for valid reset token and new password`() {
            registerUser("reset@example.com")
            verifyUserEmail("reset@example.com")

            val requestBody = """{"email":"reset@example.com"}"""
            restTemplate.postForEntity("/api/auth/password/reset-request", HttpEntity(requestBody, jsonHeaders()), String::class.java)

            val resetTokens = passwordResetTokenRepository.findAll().toList()
            assertThat(resetTokens).isNotEmpty()
            val resetToken = resetTokens.first().token

            val body = """{"token":"$resetToken","new_password":"NewSecurePass456!"}"""
            val response = restTemplate.postForEntity("/api/auth/password/reset", HttpEntity(body, jsonHeaders()), String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body).contains("Password reset successfully")
        }

        @Test
        fun `should return 400 for invalid reset token`() {
            val body = """{"token":"invalid-reset-token","new_password":"NewPass123!"}"""
            val response = restTemplate.postForEntity("/api/auth/password/reset", HttpEntity(body, jsonHeaders()), String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(response.body).contains("error")
        }
    }

    @Nested
    inner class `POST auth password change` {

        @Test
        fun `should return 200 for correct current password with valid HMAC`() {
            registerUser("change@example.com")
            verifyUserEmail("change@example.com")

            val user = userRepository.findByEmail("change@example.com")!!
            val headers = buildAuthenticatedHeaders(userId = user.id.toString())

            val body = """{"current_password":"SecurePass123!","new_password":"NewSecure456!"}"""
            val response = restTemplate.postForEntity("/api/auth/password/change", HttpEntity(body, headers), String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body).contains("Password changed successfully")
        }

        @Test
        fun `should return 401 for wrong current password`() {
            registerUser("change-fail@example.com")
            verifyUserEmail("change-fail@example.com")

            val user = userRepository.findByEmail("change-fail@example.com")!!
            val headers = buildAuthenticatedHeaders(userId = user.id.toString())

            val body = """{"current_password":"WrongPassword!","new_password":"NewSecure456!"}"""
            val response = restTemplate.postForEntity("/api/auth/password/change", HttpEntity(body, headers), String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            assertThat(response.body).contains("error")
        }

        @Test
        fun `should return 403 without HMAC headers`() {
            val body = """{"current_password":"old","new_password":"new"}"""
            val response = restTemplate.postForEntity(
                "/api/auth/password/change",
                HttpEntity(body, jsonHeaders()),
                String::class.java
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @Nested
    inner class `error response format` {

        @Test
        fun `error responses should contain error, message, and timestamp fields`() {
            val response = loginUser("nonexistent@example.com")

            assertThat(response.statusCode.is4xxClientError).isTrue()
            SoftAssertions.assertSoftly { softly ->
                softly.assertThat(response.body).contains("error")
                softly.assertThat(response.body).contains("message")
                softly.assertThat(response.body).contains("timestamp")
            }
        }
    }

    // --- Utility ---

    private fun extractJsonField(json: String, field: String): String {
        val regex = """"$field"\s*:\s*"([^"]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
            ?: throw AssertionError("Field '$field' not found in JSON: $json")
    }
}
