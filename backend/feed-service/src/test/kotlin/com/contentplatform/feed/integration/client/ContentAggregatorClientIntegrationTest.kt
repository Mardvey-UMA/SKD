package com.contentplatform.feed.integration.client

import com.contentplatform.feed.infrastructure.client.ContentAggregatorClient
import com.contentplatform.feed.infrastructure.client.ContentAggregatorClientException
import com.contentplatform.feed.infrastructure.client.model.ContentBatchResponse
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
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
class ContentAggregatorClientIntegrationTest {

    private lateinit var wireMock: WireMockServer
    private lateinit var client: ContentAggregatorClient

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

        client = ContentAggregatorClient(restClient, objectMapper)
    }

    @AfterEach
    fun tearDown() {
        wireMock.stop()
    }

    @Nested
    inner class `getContentBatch` {

        @Test
        fun `should return content batch with items map and not_found list`() {
            wireMock.stubFor(
                post(urlEqualTo("/api/v1/content/batch"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""
                                {
                                    "items": {
                                        "content-123": {
                                            "id": "content-123",
                                            "title": "Test Article",
                                            "description": "Description",
                                            "content": "Full content",
                                            "content_format": "html",
                                            "source_type": "telegram",
                                            "source_subtype": "channel",
                                            "url": "https://example.com/article",
                                            "published_at": "2026-04-05T10:00:00Z",
                                            "author_name": "Author",
                                            "media": [{"type": "image", "url": "https://img.com/1.jpg"}],
                                            "metadata": {"views": 42},
                                            "related_ids": ["content-456"]
                                        }
                                    },
                                    "not_found": ["content-999"]
                                }
                            """.trimIndent())
                    )
            )

            val result = client.getContentBatch(
                ids = listOf("content-123", "content-999"),
                includeRelated = true,
                relatedLimit = 5
            )

            assertThat(result.items).hasSize(1)
            assertThat(result.items).containsKey("content-123")
            assertThat(result.notFound).containsExactly("content-999")

            val item = result.items["content-123"]!!
            assertThat(item.id).isEqualTo("content-123")
            assertThat(item.title).isEqualTo("Test Article")
            assertThat(item.sourceType).isEqualTo("telegram")
            assertThat(item.relatedIds).containsExactly("content-456")
        }

        @Test
        fun `should send correct request body with ids, include_related, and related_limit`() {
            wireMock.stubFor(
                post(urlEqualTo("/api/v1/content/batch"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""{"items": {}, "not_found": []}""")
                    )
            )

            client.getContentBatch(
                ids = listOf("content-1", "content-2"),
                includeRelated = true,
                relatedLimit = 3
            )

            wireMock.verify(
                postRequestedFor(urlEqualTo("/api/v1/content/batch"))
                    .withRequestBody(
                        equalToJson(
                            """{"ids": ["content-1", "content-2"], "include_related": true, "related_limit": 3}""",
                            true,
                            false
                        )
                    )
            )
        }

        @Test
        fun `should handle response where all items are found`() {
            wireMock.stubFor(
                post(urlEqualTo("/api/v1/content/batch"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""
                                {
                                    "items": {
                                        "content-1": {
                                            "id": "content-1",
                                            "title": "First",
                                            "content_format": "text",
                                            "source_type": "rss",
                                            "url": "https://example.com/1",
                                            "published_at": "2026-04-05T10:00:00Z",
                                            "media": [],
                                            "related_ids": []
                                        },
                                        "content-2": {
                                            "id": "content-2",
                                            "title": "Second",
                                            "content_format": "html",
                                            "source_type": "telegram",
                                            "url": "https://example.com/2",
                                            "published_at": "2026-04-05T11:00:00Z",
                                            "media": [],
                                            "related_ids": []
                                        }
                                    },
                                    "not_found": []
                                }
                            """.trimIndent())
                    )
            )

            val result = client.getContentBatch(
                ids = listOf("content-1", "content-2"),
                includeRelated = false,
                relatedLimit = 0
            )

            assertThat(result.items).hasSize(2)
            assertThat(result.notFound).isEmpty()
        }

        @Test
        fun `should handle response where all items are not found`() {
            wireMock.stubFor(
                post(urlEqualTo("/api/v1/content/batch"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody("""
                                {
                                    "items": {},
                                    "not_found": ["content-1", "content-2", "content-3"]
                                }
                            """.trimIndent())
                    )
            )

            val result = client.getContentBatch(
                ids = listOf("content-1", "content-2", "content-3"),
                includeRelated = false,
                relatedLimit = 0
            )

            assertThat(result.items).isEmpty()
            assertThat(result.notFound).hasSize(3)
        }

        @Test
        fun `should throw ContentAggregatorClientException on 500 server error`() {
            wireMock.stubFor(
                post(urlEqualTo("/api/v1/content/batch"))
                    .willReturn(aResponse().withStatus(500))
            )

            assertThatThrownBy {
                client.getContentBatch(
                    ids = listOf("content-1"),
                    includeRelated = false,
                    relatedLimit = 0
                )
            }.isInstanceOf(ContentAggregatorClientException::class.java)
        }

        @Test
        fun `should throw ContentAggregatorClientException on 503 service unavailable`() {
            wireMock.stubFor(
                post(urlEqualTo("/api/v1/content/batch"))
                    .willReturn(aResponse().withStatus(503))
            )

            assertThatThrownBy {
                client.getContentBatch(
                    ids = listOf("content-1"),
                    includeRelated = false,
                    relatedLimit = 0
                )
            }.isInstanceOf(ContentAggregatorClientException::class.java)
        }
    }
}
