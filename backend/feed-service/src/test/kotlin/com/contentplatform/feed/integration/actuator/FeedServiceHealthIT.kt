package com.contentplatform.feed.integration.actuator

import com.contentplatform.feed.integration.IntegrationTestBase
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@Tag("integration")
class FeedServiceHealthIT : IntegrationTestBase() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    companion object {
        @JvmStatic
        val recSystemWireMock = WireMockServer(wireMockConfig().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun startWireMock() {
            recSystemWireMock.start()
        }

        @AfterAll
        @JvmStatic
        fun stopWireMock() {
            recSystemWireMock.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun wireMockProperties(registry: DynamicPropertyRegistry) {
            registry.add("services.rec-system.url") { "http://localhost:${recSystemWireMock.port()}" }
        }
    }

    @BeforeEach
    fun stubRecSystemHealth() {
        recSystemWireMock.stubFor(
            get(urlEqualTo("/health"))
                .willReturn(aResponse().withStatus(200))
        )
    }

    @Test
    fun `GET actuator health returns 200 with all custom indicators`() {
        val response = restTemplate.getForEntity("/actuator/health", String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val body = response.body!!
        assertThat(body).contains("\"status\"")
        assertThat(body).contains("recSystem")
        assertThat(body).contains("feedSchema")
        assertThat(body).contains("redis")
        assertThat(body).contains("db")
    }

    @Test
    fun `GET actuator health shows recSystem indicator as UP when rec-system is healthy`() {
        val response = restTemplate.getForEntity("/actuator/health", String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val body = response.body!!
        assertThat(body).contains("\"recSystem\"")
        assertThat(body).containsPattern("\"recSystem\"\\s*:\\s*\\{\\s*\"status\"\\s*:\\s*\"UP\"")
    }

    @Test
    fun `GET actuator health shows feedSchema indicator as UP`() {
        val response = restTemplate.getForEntity("/actuator/health", String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val body = response.body!!
        assertThat(body).contains("\"feedSchema\"")
        assertThat(body).containsPattern("\"feedSchema\"\\s*:\\s*\\{\\s*\"status\"\\s*:\\s*\"UP\"")
    }

    @Test
    fun `GET actuator health shows liveness and readiness sub-paths`() {
        val liveness = restTemplate.getForEntity("/actuator/health/liveness", String::class.java)
        val readiness = restTemplate.getForEntity("/actuator/health/readiness", String::class.java)

        assertThat(liveness.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(readiness.statusCode).isEqualTo(HttpStatus.OK)
    }
}
