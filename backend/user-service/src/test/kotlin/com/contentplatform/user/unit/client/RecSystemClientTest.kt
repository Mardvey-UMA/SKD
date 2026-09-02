package com.contentplatform.user.unit.client

import com.contentplatform.user.configuration.RecSystemProperties
import com.contentplatform.user.infrastructure.client.RecSystemClient
import com.contentplatform.user.infrastructure.client.RecSystemException
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.util.UUID

@Tag("unit")
class RecSystemClientTest {

    private val restClient = mockk<RestClient>()
    private val properties = RecSystemProperties(
        url = "http://localhost:8000",
        timeoutMs = 5000,
        retryMaxAttempts = 3,
        retryDelayMs = 0 // no delay in unit tests
    )
    private val client = RecSystemClient(restClient, properties)

    @Nested
    inner class `RecSystemProperties defaults` {

        @Test
        fun `should default to 6 retries with 2000ms delay`() {
            val defaults = RecSystemProperties()
            assertThat(defaults.retryMaxAttempts).isEqualTo(6)
            assertThat(defaults.retryDelayMs).isEqualTo(2000L)
        }
    }

    @Nested
    inner class `postOnboarding` {

        @Test
        fun `should return response body on successful first attempt`() {
            val userId = UUID.randomUUID()
            val categories = listOf("technology", "science", "business")
            val sourceContentIds = listOf(UUID.randomUUID().toString())

            val expectedBody = mapOf("user_id" to userId.toString(), "profile_initialized" to true)

            val requestBodySpec = mockk<RestClient.RequestBodySpec>()
            val requestBodyUriSpec = mockk<RestClient.RequestBodyUriSpec>()
            val responseSpec = mockk<RestClient.ResponseSpec>()

            every { restClient.post() } returns requestBodyUriSpec
            every { requestBodyUriSpec.uri("/onboarding") } returns requestBodySpec
            every { requestBodySpec.contentType(any()) } returns requestBodySpec
            every { requestBodySpec.body(any<Any>()) } returns requestBodySpec
            every { requestBodySpec.retrieve() } returns responseSpec
            every { responseSpec.body(Map::class.java) } returns expectedBody

            val result = client.postOnboarding(userId, categories, sourceContentIds)

            assertThat(result).containsEntry("profile_initialized", true)
            assertThat(result).containsEntry("user_id", userId.toString())
        }

        @Test
        fun `should retry on 404 and succeed on second attempt`() {
            val userId = UUID.randomUUID()
            val categories = listOf("technology", "science")
            val sourceContentIds = emptyList<String>()

            val expectedBody = mapOf("user_id" to userId.toString(), "profile_initialized" to true)

            val requestBodySpec = mockk<RestClient.RequestBodySpec>()
            val requestBodyUriSpec = mockk<RestClient.RequestBodyUriSpec>()
            val responseSpec = mockk<RestClient.ResponseSpec>()

            every { restClient.post() } returns requestBodyUriSpec
            every { requestBodyUriSpec.uri("/onboarding") } returns requestBodySpec
            every { requestBodySpec.contentType(any()) } returns requestBodySpec
            every { requestBodySpec.body(any<Any>()) } returns requestBodySpec

            // First call throws 404, second succeeds
            every { requestBodySpec.retrieve() } throws
                HttpClientErrorException(HttpStatus.NOT_FOUND) andThen responseSpec
            every { responseSpec.body(Map::class.java) } returns expectedBody

            val result = client.postOnboarding(userId, categories, sourceContentIds)

            assertThat(result).containsEntry("profile_initialized", true)
        }

        @Test
        fun `should throw RecSystemException after exhausting retries on 404`() {
            val userId = UUID.randomUUID()
            val categories = listOf("technology", "science", "business")
            val sourceContentIds = emptyList<String>()

            val requestBodySpec = mockk<RestClient.RequestBodySpec>()
            val requestBodyUriSpec = mockk<RestClient.RequestBodyUriSpec>()

            every { restClient.post() } returns requestBodyUriSpec
            every { requestBodyUriSpec.uri("/onboarding") } returns requestBodySpec
            every { requestBodySpec.contentType(any()) } returns requestBodySpec
            every { requestBodySpec.body(any<Any>()) } returns requestBodySpec

            // All attempts throw 404
            every { requestBodySpec.retrieve() } throws
                HttpClientErrorException(HttpStatus.NOT_FOUND)

            assertThatThrownBy {
                client.postOnboarding(userId, categories, sourceContentIds)
            }
                .isInstanceOf(RecSystemException::class.java)
                .hasMessageContaining("404")
        }
    }

    @Nested
    inner class `getCategories` {

        @Test
        fun `should return response body on success`() {
            val expectedBody = """{"categories":[{"id":"technology","name":"Technology"}],"min_select":3,"max_select":5}"""

            val requestHeadersUriSpec = mockk<RestClient.RequestHeadersUriSpec<*>>()
            val requestHeadersSpec = mockk<RestClient.RequestHeadersSpec<*>>()
            val responseSpec = mockk<RestClient.ResponseSpec>()

            every { restClient.get() } returns requestHeadersUriSpec
            every { requestHeadersUriSpec.uri("/categories?locale={locale}", "ru") } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.body(String::class.java) } returns expectedBody

            val result = client.getCategories("ru")

            assertThat(result).contains("technology")
            assertThat(result).contains("min_select")
        }

        @Test
        fun `should propagate exception on HTTP error`() {
            val requestHeadersUriSpec = mockk<RestClient.RequestHeadersUriSpec<*>>()
            val requestHeadersSpec = mockk<RestClient.RequestHeadersSpec<*>>()

            every { restClient.get() } returns requestHeadersUriSpec
            every { requestHeadersUriSpec.uri("/categories?locale={locale}", "ru") } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } throws
                HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR)

            assertThatThrownBy {
                client.getCategories("ru")
            }.isInstanceOf(HttpClientErrorException::class.java)
        }
    }
}
