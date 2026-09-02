package com.contentplatform.gateway.integration

import com.contentplatform.gateway.config.GatewayProperties
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * Integration test verifying that GatewayProperties binds correctly
 * from application.yml in a real Spring context.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayPropertiesIntegrationTest {

    companion object {
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)

        @JvmStatic
        val jwksWireMock = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @BeforeAll
        fun startContainers() {
            redis.start()
            jwksWireMock.start()
            jwksWireMock.stubFor(
                get(urlPathEqualTo("/.well-known/jwks.json"))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"keys":[]}""")
                    )
            )
        }

        @JvmStatic
        @AfterAll
        fun stopContainers() {
            jwksWireMock.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") {
                "http://localhost:${jwksWireMock.port()}/.well-known/jwks.json"
            }
        }
    }

    @Autowired
    lateinit var gatewayProperties: GatewayProperties

    @Test
    fun `should bind all routes from application yml`() {
        assertThat(gatewayProperties.routes).isNotEmpty
        assertThat(gatewayProperties.routes.size).isGreaterThanOrEqualTo(7)
    }

    @Test
    fun `should bind auth route as public`() {
        val authRoute = gatewayProperties.routes.find { it.pathPrefix == "/api/auth" }
        assertThat(authRoute).isNotNull
        assertThat(authRoute!!.public).isTrue()
        assertThat(authRoute.target).contains("auth-service")
    }

    @Test
    fun `should bind config route with SUBSCRIBER required role`() {
        val configRoute = gatewayProperties.routes.find { it.pathPrefix == "/api/config" }
        assertThat(configRoute).isNotNull
        assertThat(configRoute!!.requiredRoles).containsExactly("SUBSCRIBER")
    }

    @Test
    fun `should bind rate limit properties`() {
        assertThat(gatewayProperties.rateLimit.capacity).isEqualTo(100)
        assertThat(gatewayProperties.rateLimit.windowSeconds).isEqualTo(60)
        assertThat(gatewayProperties.rateLimit.overdraft).isEqualTo(20)
    }

    @Test
    fun `should bind cors properties`() {
        assertThat(gatewayProperties.cors.allowedOrigins).isNotEmpty
        assertThat(gatewayProperties.cors.allowCredentials).isTrue()
        assertThat(gatewayProperties.cors.maxAge).isEqualTo(3600)
    }

    @Test
    fun `should bind hmac secret from test profile`() {
        assertThat(gatewayProperties.hmacSecret).isNotBlank()
    }

    @Test
    fun `should bind default timeout`() {
        assertThat(gatewayProperties.defaultTimeoutMs).isEqualTo(2000)
    }
}
