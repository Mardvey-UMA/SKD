package com.contentplatform.user.integration.client

import com.contentplatform.user.configuration.RecSystemProperties
import com.contentplatform.user.infrastructure.client.RecSystemClient
import com.contentplatform.user.infrastructure.client.RecSystemException
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.util.UUID

@Tag("integration")
class RecSystemClientIntegrationTest {

    private lateinit var wireMock: WireMockServer
    private lateinit var client: RecSystemClient

    @BeforeEach
    fun setUp() {
        wireMock = WireMockServer(wireMockConfig().dynamicPort())
        wireMock.start()

        val properties = RecSystemProperties(
            url = "http://localhost:${wireMock.port()}",
            timeoutMs = 5000,
            retryMaxAttempts = 3,
            retryDelayMs = 100 // short delay for tests
        )

        val restClient = RestClient.builder()
            .baseUrl(properties.url)
            .build()

        client = RecSystemClient(restClient, properties)
    }

    @AfterEach
    fun tearDown() {
        wireMock.stop()
    }

    @Nested
    inner class `POST onboarding` {

        @Test
        fun `should return parsed response on 200`() {
            val userId = UUID.randomUUID()

            wireMock.stubFor(
                post(urlEqualTo("/onboarding"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""{"user_id":"$userId","profile_initialized":true}""")
                    )
            )

            val result = client.postOnboarding(
                userId = userId,
                categories = listOf("technology", "science", "business"),
                sourceContentIds = emptyList()
            )

            assertThat(result).containsEntry("user_id", userId.toString())
            assertThat(result).containsEntry("profile_initialized", true)

            wireMock.verify(1, postRequestedFor(urlEqualTo("/onboarding")))
        }

        @Test
        fun `should retry on 404 and succeed on second attempt`() {
            val userId = UUID.randomUUID()

            // First call returns 404, second returns 200 (using Scenarios)
            wireMock.stubFor(
                post(urlEqualTo("/onboarding"))
                    .inScenario("retry")
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(aResponse().withStatus(404))
                    .willSetStateTo("second-attempt")
            )
            wireMock.stubFor(
                post(urlEqualTo("/onboarding"))
                    .inScenario("retry")
                    .whenScenarioStateIs("second-attempt")
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""{"user_id":"$userId","profile_initialized":true}""")
                    )
            )

            val result = client.postOnboarding(
                userId = userId,
                categories = listOf("technology", "science"),
                sourceContentIds = emptyList()
            )

            assertThat(result).containsEntry("profile_initialized", true)
            wireMock.verify(2, postRequestedFor(urlEqualTo("/onboarding")))
        }

        @Test
        fun `should throw RecSystemException after 3 retries on 404`() {
            wireMock.stubFor(
                post(urlEqualTo("/onboarding"))
                    .willReturn(aResponse().withStatus(404))
            )

            val userId = UUID.randomUUID()

            assertThatThrownBy {
                client.postOnboarding(
                    userId = userId,
                    categories = listOf("technology", "science", "business"),
                    sourceContentIds = emptyList()
                )
            }.isInstanceOf(RecSystemException::class.java)

            wireMock.verify(3, postRequestedFor(urlEqualTo("/onboarding")))
        }

        @Test
        fun `should send correct request body`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID().toString()

            wireMock.stubFor(
                post(urlEqualTo("/onboarding"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""{"user_id":"$userId","profile_initialized":true}""")
                    )
            )

            client.postOnboarding(
                userId = userId,
                categories = listOf("technology", "science"),
                sourceContentIds = listOf(contentId)
            )

            wireMock.verify(
                postRequestedFor(urlEqualTo("/onboarding"))
                    .withRequestBody(
                        equalToJson(
                            """{"user_id":"$userId","categories":["technology","science"],"source_content_ids":["$contentId"]}""",
                            true, // ignoreArrayOrder
                            false // ignoreExtraElements
                        )
                    )
            )
        }
    }

    @Nested
    inner class `GET categories` {

        @Test
        fun `should return categories response body`() {
            val responseBody = """{"categories":[{"id":"technology","name":"Technology","icon":"laptop"}],"min_select":3,"max_select":5}"""

            wireMock.stubFor(
                get(urlEqualTo("/categories?locale=ru"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(responseBody)
                    )
            )

            val result = client.getCategories("ru")

            assertThat(result).contains("technology")
            assertThat(result).contains("min_select")
            assertThat(result).contains("max_select")
        }

        @Test
        fun `should throw exception when rec-system returns 500`() {
            wireMock.stubFor(
                get(urlEqualTo("/categories?locale=ru"))
                    .willReturn(aResponse().withStatus(500))
            )

            assertThatThrownBy {
                client.getCategories("ru")
            }.isInstanceOf(Exception::class.java)
        }
    }
}
