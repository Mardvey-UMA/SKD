package com.skd.userinteractions.unit

import com.skd.userinteractions.api.GlobalExceptionHandler
import com.skd.userinteractions.api.model.ErrorResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.ServletWebRequest
import java.time.Instant
import java.util.UUID

@Tag("unit")
class GlobalExceptionHandlerTest {

    private lateinit var handler: GlobalExceptionHandler

    @BeforeEach
    fun setUp() {
        handler = GlobalExceptionHandler()
    }

    private fun mockRequest(requestId: String? = UUID.randomUUID().toString()): ServletWebRequest {
        val servletRequest = MockHttpServletRequest()
        if (requestId != null) {
            servletRequest.addHeader("X-Request-Id", requestId)
        }
        return ServletWebRequest(servletRequest)
    }

    @Nested
    inner class `IllegalArgumentException handling` {

        @Test
        fun `returns 422 Unprocessable Entity`() {
            val exception = IllegalArgumentException("Batch must not be empty")
            val request = mockRequest()

            val response = handler.handleIllegalArgument(exception, request)

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        }

        @Test
        fun `response body contains error and message fields`() {
            val exception = IllegalArgumentException("Batch size exceeds maximum of 100")
            val request = mockRequest()

            val response = handler.handleIllegalArgument(exception, request)
            val body = response.body!!

            assertThat(body.error).isEqualTo("Unprocessable Entity")
            assertThat(body.message).isEqualTo("Batch size exceeds maximum of 100")
        }

        @Test
        fun `response body contains requestId from header`() {
            val requestId = UUID.randomUUID().toString()
            val exception = IllegalArgumentException("invalid")
            val request = mockRequest(requestId)

            val response = handler.handleIllegalArgument(exception, request)
            val body = response.body!!

            assertThat(body.requestId).isEqualTo(requestId)
        }

        @Test
        fun `response body contains timestamp`() {
            val before = Instant.now()
            val exception = IllegalArgumentException("invalid")
            val request = mockRequest()

            val response = handler.handleIllegalArgument(exception, request)
            val body = response.body!!

            assertThat(body.timestamp).isNotNull()
            // timestamp should be parseable and recent
            val ts = Instant.parse(body.timestamp)
            assertThat(ts).isAfterOrEqualTo(before)
        }
    }

    @Nested
    inner class `generic Exception handling` {

        @Test
        fun `returns 500 Internal Server Error`() {
            val exception = RuntimeException("Something went wrong")
            val request = mockRequest()

            val response = handler.handleGenericException(exception, request)

            assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        }

        @Test
        fun `response body contains error field`() {
            val exception = RuntimeException("Something went wrong")
            val request = mockRequest()

            val response = handler.handleGenericException(exception, request)
            val body = response.body!!

            assertThat(body.error).isEqualTo("Internal Server Error")
            assertThat(body.message).isEqualTo("Something went wrong")
        }
    }

    @Nested
    inner class `ErrorResponse structure` {

        @Test
        fun `ErrorResponse has all required fields`() {
            val errorResponse = ErrorResponse(
                error = "Unprocessable Entity",
                message = "Batch must not be empty",
                requestId = "req-123",
                timestamp = Instant.now().toString(),
                details = null
            )

            assertThat(errorResponse.error).isNotNull()
            assertThat(errorResponse.message).isNotNull()
            assertThat(errorResponse.requestId).isNotNull()
            assertThat(errorResponse.timestamp).isNotNull()
        }

        @Test
        fun `ErrorResponse supports optional details map`() {
            val details = mapOf("field" to "actionType" as Any, "reason" to "unknown value" as Any)
            val errorResponse = ErrorResponse(
                error = "Unprocessable Entity",
                message = "Validation failed",
                requestId = "req-456",
                timestamp = Instant.now().toString(),
                details = details
            )

            assertThat(errorResponse.details).containsEntry("field", "actionType")
            assertThat(errorResponse.details).containsEntry("reason", "unknown value")
        }
    }
}
