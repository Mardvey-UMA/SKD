package com.contentplatform.auth.unit.controller

import com.contentplatform.auth.api.controller.GlobalExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest

@Tag("unit")
class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    private fun mockRequest(requestId: String? = null): MockHttpServletRequest {
        val request = MockHttpServletRequest()
        if (requestId != null) {
            request.addHeader("X-Request-Id", requestId)
        }
        return request
    }

    @Test
    fun `should map IllegalArgumentException to 400 BAD_REQUEST`() {
        val request = mockRequest("req-123")
        val response = handler.handleIllegalArgument(
            IllegalArgumentException("Invalid input"),
            request
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        val body = response.body!!
        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(body["error"]).isEqualTo("bad_request")
            softly.assertThat(body["message"]).isEqualTo("Invalid input")
            softly.assertThat(body["request_id"]).isEqualTo("req-123")
            softly.assertThat(body["timestamp"]).isNotNull()
        }
    }

    @Test
    fun `should map DuplicateEmailException to 409 CONFLICT`() {
        val request = mockRequest("req-456")
        val exception = com.contentplatform.auth.api.exception.DuplicateEmailException("Email already registered")
        val response = handler.handleDuplicateEmail(exception, request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        val body = response.body!!
        assertThat(body["error"]).isEqualTo("duplicate_email")
        assertThat(body["message"]).isEqualTo("Email already registered")
    }

    @Test
    fun `should map InvalidCredentialsException to 401 UNAUTHORIZED`() {
        val request = mockRequest()
        val exception = com.contentplatform.auth.api.exception.InvalidCredentialsException("Invalid credentials")
        val response = handler.handleInvalidCredentials(exception, request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        val body = response.body!!
        assertThat(body["error"]).isEqualTo("invalid_credentials")
    }

    @Test
    fun `should map EmailNotVerifiedException to 403 FORBIDDEN`() {
        val request = mockRequest()
        val exception = com.contentplatform.auth.api.exception.EmailNotVerifiedException("Email not verified")
        val response = handler.handleEmailNotVerified(exception, request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        val body = response.body!!
        assertThat(body["error"]).isEqualTo("email_not_verified")
    }

    @Test
    fun `should map TokenNotFoundException to 404 NOT_FOUND`() {
        val request = mockRequest()
        val exception = com.contentplatform.auth.api.exception.TokenNotFoundException("Token not found")
        val response = handler.handleTokenNotFound(exception, request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        val body = response.body!!
        assertThat(body["error"]).isEqualTo("token_not_found")
    }

    @Test
    fun `should map TokenExpiredException to 400 BAD_REQUEST`() {
        val request = mockRequest()
        val exception = com.contentplatform.auth.api.exception.TokenExpiredException("Token expired")
        val response = handler.handleTokenExpired(exception, request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        val body = response.body!!
        assertThat(body["error"]).isEqualTo("token_expired")
    }

    @Test
    fun `should map ValidationException to 422 UNPROCESSABLE_ENTITY`() {
        val request = mockRequest()
        val exception = com.contentplatform.auth.api.exception.ValidationException("Password too short")
        val response = handler.handleValidation(exception, request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        val body = response.body!!
        assertThat(body["error"]).isEqualTo("validation_error")
        assertThat(body["message"]).isEqualTo("Password too short")
    }

    @Test
    fun `should use null request_id when header is missing`() {
        val request = mockRequest(requestId = null)
        val response = handler.handleIllegalArgument(
            IllegalArgumentException("test"),
            request
        )

        val body = response.body!!
        assertThat(body["request_id"]).isNull()
    }

    @Test
    fun `should include ISO8601 timestamp in all error responses`() {
        val request = mockRequest()
        val response = handler.handleIllegalArgument(
            IllegalArgumentException("test"),
            request
        )

        val body = response.body!!
        val timestamp = body["timestamp"] as String
        // ISO8601 format check -- should contain "T" and timezone info
        assertThat(timestamp).containsPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")
    }
}
