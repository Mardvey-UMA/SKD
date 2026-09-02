package com.contentplatform.feed.unit.health

import com.contentplatform.feed.infrastructure.health.RecSystemHealthIndicator
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status
import org.springframework.web.client.RestClient

@Tag("unit")
class RecSystemHealthIndicatorTest {

    private lateinit var wireMock: WireMockServer
    private lateinit var indicator: RecSystemHealthIndicator

    @BeforeEach
    fun setUp() {
        wireMock = WireMockServer(wireMockConfig().dynamicPort())
        wireMock.start()
        val restClient = RestClient.builder()
            .baseUrl("http://localhost:${wireMock.port()}")
            .build()
        indicator = RecSystemHealthIndicator(restClient)
    }

    @AfterEach
    fun tearDown() {
        wireMock.stop()
    }

    @Test
    fun `returns UP when rec-system health endpoint returns 200`() {
        wireMock.stubFor(
            get(urlEqualTo("/health"))
                .willReturn(aResponse().withStatus(200))
        )

        val health = indicator.health()

        assertThat(health.status).isEqualTo(Status.UP)
    }

    @Test
    fun `returns DOWN when rec-system health endpoint returns 500`() {
        wireMock.stubFor(
            get(urlEqualTo("/health"))
                .willReturn(aResponse().withStatus(500))
        )

        val health = indicator.health()

        assertThat(health.status).isEqualTo(Status.DOWN)
        assertThat(health.details["httpStatus"]).isEqualTo(500)
    }

    @Test
    fun `returns DOWN when rec-system health endpoint returns 503`() {
        wireMock.stubFor(
            get(urlEqualTo("/health"))
                .willReturn(aResponse().withStatus(503))
        )

        val health = indicator.health()

        assertThat(health.status).isEqualTo(Status.DOWN)
    }

    @Test
    fun `returns DOWN when rec-system is unreachable`() {
        wireMock.stop()

        val health = indicator.health()

        assertThat(health.status).isEqualTo(Status.DOWN)
    }
}
