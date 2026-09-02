package com.contentplatform.feed.unit.api

import com.contentplatform.feed.api.exception.GlobalExceptionHandler
import com.contentplatform.feed.application.exception.AlreadyExistsException
import com.contentplatform.feed.api.exception.NotFoundException
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
    fun `should map AlreadyExistsException to 409 CONFLICT`() {
        val request = mockRequest("req-123")
        val exception = AlreadyExistsException("Bookmark already exists")
        val response = handler.handleAlreadyExists(exception, request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        val body = response.body!!
        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(body["error"]).isEqualTo("conflict")
            softly.assertThat(body["message"]).isEqualTo("Bookmark already exists")
            softly.assertThat(body["request_id"]).isEqualTo("req-123")
            softly.assertThat(body["timestamp"]).isNotNull()
        }
    }

    @Test
    fun `should map NotFoundException to 404 NOT_FOUND`() {
        val request = mockRequest("req-456")
        val exception = NotFoundException("Content not found")
        val response = handler.handleNotFound(exception, request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        val body = response.body!!
        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(body["error"]).isEqualTo("not_found")
            softly.assertThat(body["message"]).isEqualTo("Content not found")
            softly.assertThat(body["request_id"]).isEqualTo("req-456")
            softly.assertThat(body["timestamp"]).isNotNull()
        }
    }

    @Test
    fun `should map generic Exception to 500 INTERNAL_SERVER_ERROR`() {
        val request = mockRequest("req-789")
        val exception = RuntimeException("Unexpected error")
        val response = handler.handleGenericException(exception, request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        val body = response.body!!
        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(body["error"]).isEqualTo("internal_error")
            softly.assertThat(body["message"]).isEqualTo("An unexpected error occurred")
            softly.assertThat(body["request_id"]).isEqualTo("req-789")
            softly.assertThat(body["timestamp"]).isNotNull()
        }
    }

    @Test
    fun `should map IllegalArgumentException to 400 BAD_REQUEST`() {
        val request = mockRequest()
        val exception = IllegalArgumentException("Invalid cursor format")
        val response = handler.handleIllegalArgument(exception, request)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        val body = response.body!!
        assertThat(body["error"]).isEqualTo("validation_error")
        assertThat(body["message"]).isEqualTo("Invalid cursor format")
    }

    @Test
    fun `should use null request_id when header is missing`() {
        val request = mockRequest(requestId = null)
        val response = handler.handleAlreadyExists(
            AlreadyExistsException("test"),
            request
        )

        val body = response.body!!
        assertThat(body["request_id"]).isNull()
    }

    @Test
    fun `should include ISO8601 timestamp in all error responses`() {
        val request = mockRequest()
        val response = handler.handleNotFound(
            NotFoundException("test"),
            request
        )

        val body = response.body!!
        val timestamp = body["timestamp"] as String
        assertThat(timestamp).containsPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")
    }
}
