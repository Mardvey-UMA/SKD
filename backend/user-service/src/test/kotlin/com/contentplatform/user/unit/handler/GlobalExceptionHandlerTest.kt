package com.contentplatform.user.unit.handler

import com.contentplatform.user.api.handler.GlobalExceptionHandler
import com.contentplatform.user.application.exception.NotFoundException
import com.contentplatform.user.application.exception.OnboardingAlreadyCompletedException
import com.contentplatform.user.infrastructure.client.RecSystemException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

@Tag("unit")
class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `should return 404 with error body for NotFoundException`() {
        val exception = NotFoundException("Profile not found for userId=abc-123")

        val response = handler.handleNotFoundException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body).isNotNull
        val body = response.body!!
        assertThat(body["error"]).isEqualTo("NOT_FOUND")
        assertThat(body["message"]).isEqualTo("Profile not found for userId=abc-123")
        assertThat(body["timestamp"]).isNotNull
    }

    @Test
    fun `should return 502 with error body for RecSystemException`() {
        val exception = RecSystemException("rec-system /onboarding failed after 3 attempts with 404")

        val response = handler.handleRecSystemException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
        assertThat(response.body).isNotNull
        val body = response.body!!
        assertThat(body["error"]).isEqualTo("BAD_GATEWAY")
        assertThat(body["message"]).isEqualTo("rec-system /onboarding failed after 3 attempts with 404")
        assertThat(body["timestamp"]).isNotNull
    }

    @Test
    fun `should return 409 with error body for OnboardingAlreadyCompletedException`() {
        val exception = OnboardingAlreadyCompletedException("Onboarding already completed")

        val response = handler.handleOnboardingAlreadyCompleted(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body).isNotNull
        val body = response.body!!
        assertThat(body["error"]).isEqualTo("CONFLICT")
        assertThat(body["message"]).isEqualTo("Onboarding already completed")
        assertThat(body["timestamp"]).isNotNull
    }

    @Test
    fun `should return 500 with error body for generic Exception`() {
        val exception = RuntimeException("Something unexpected happened")

        val response = handler.handleGenericException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body).isNotNull
        val body = response.body!!
        assertThat(body["error"]).isEqualTo("INTERNAL_SERVER_ERROR")
        assertThat(body["message"]).isNotNull
        assertThat(body["timestamp"]).isNotNull
    }
}
