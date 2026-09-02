package com.contentplatform.feed.integration.client

import com.contentplatform.feed.infrastructure.client.RecSystemClient
import com.contentplatform.feed.infrastructure.client.RecSystemClientException
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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

    private val objectMapper = jacksonObjectMapper().apply {
        propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @BeforeEach
    fun setUp() {
        wireMock = WireMockServer(wireMockConfig().dynamicPort())
        wireMock.start()

        val restClient = RestClient.builder()
            .baseUrl("http://localhost:${wireMock.port()}")
            .build()

        client = RecSystemClient(restClient, objectMapper)
    }

    @AfterEach
    fun tearDown() {
        wireMock.stop()
    }

    @Nested
    inner class `getRecommendations` {

        @Test
        fun `should return list of content IDs on successful response`() {
            val userId = UUID.randomUUID()
            val contentId1 = UUID.randomUUID()
            val contentId2 = UUID.randomUUID()
            val contentId3 = UUID.randomUUID()

            wireMock.stubFor(
                post(urlEqualTo("/recommendations"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""
                                {
                                    "user_id": "$userId",
                                    "items": ["$contentId1", "$contentId2", "$contentId3"],
                                    "count": 3,
                                    "generated_at": "2026-04-05T10:00:00Z"
                                }
                            """.trimIndent())
                    )
            )

            val result = client.getRecommendations(userId, 3)

            assertThat(result).isNotNull
            assertThat(result).containsExactly(contentId1, contentId2, contentId3)
        }

        @Test
        fun `should send correct request body with user_id and count`() {
            val userId = UUID.randomUUID()

            wireMock.stubFor(
                post(urlEqualTo("/recommendations"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""
                                {
                                    "user_id": "$userId",
                                    "items": [],
                                    "count": 0,
                                    "generated_at": "2026-04-05T10:00:00Z"
                                }
                            """.trimIndent())
                    )
            )

            client.getRecommendations(userId, 120)

            wireMock.verify(
                postRequestedFor(urlEqualTo("/recommendations"))
                    .withRequestBody(
                        equalToJson(
                            """{"user_id": "$userId", "count": 120}""",
                            true,
                            false
                        )
                    )
            )
        }

        @Test
        fun `should return empty list when response items is empty`() {
            val userId = UUID.randomUUID()

            wireMock.stubFor(
                post(urlEqualTo("/recommendations"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""
                                {
                                    "user_id": "$userId",
                                    "items": [],
                                    "count": 0,
                                    "generated_at": "2026-04-05T10:00:00Z"
                                }
                            """.trimIndent())
                    )
            )

            val result = client.getRecommendations(userId, 120)

            assertThat(result).isEmpty()
        }

        @Test
        fun `should return null when rec-system returns 404`() {
            val userId = UUID.randomUUID()

            wireMock.stubFor(
                post(urlEqualTo("/recommendations"))
                    .willReturn(aResponse().withStatus(404))
            )

            val result = client.getRecommendations(userId, 120)

            assertThat(result).isNull()
        }

        @Test
        fun `should throw RecSystemClientException on 500 server error`() {
            val userId = UUID.randomUUID()

            wireMock.stubFor(
                post(urlEqualTo("/recommendations"))
                    .willReturn(aResponse().withStatus(500))
            )

            assertThatThrownBy {
                client.getRecommendations(userId, 120)
            }.isInstanceOf(RecSystemClientException::class.java)
        }
    }

    @Nested
    inner class `getColdStart` {

        @Test
        fun `should return list of content IDs for cold start`() {
            val contentId1 = UUID.randomUUID()
            val contentId2 = UUID.randomUUID()

            wireMock.stubFor(
                get(urlPathEqualTo("/recommendations/cold-start"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""
                                {
                                    "items": ["$contentId1", "$contentId2"],
                                    "count": 2,
                                    "generated_at": "2026-04-05T10:00:00Z"
                                }
                            """.trimIndent())
                    )
            )

            val result = client.getColdStart(20)

            assertThat(result).containsExactly(contentId1, contentId2)
        }

        @Test
        fun `should return empty list when cold start has no items`() {
            wireMock.stubFor(
                get(urlPathEqualTo("/recommendations/cold-start"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""
                                {
                                    "items": [],
                                    "count": 0,
                                    "generated_at": "2026-04-05T10:00:00Z"
                                }
                            """.trimIndent())
                    )
            )

            val result = client.getColdStart(20)

            assertThat(result).isEmpty()
        }

        @Test
        fun `should throw RecSystemClientException on 500 server error`() {
            wireMock.stubFor(
                get(urlPathEqualTo("/recommendations/cold-start"))
                    .willReturn(aResponse().withStatus(500))
            )

            assertThatThrownBy {
                client.getColdStart(20)
            }.isInstanceOf(RecSystemClientException::class.java)
        }
    }
}
