package com.contentplatform.auth.integration.configuration

import com.contentplatform.auth.configuration.AuthProperties
import com.contentplatform.auth.configuration.GatewayProperties
import com.contentplatform.auth.configuration.JwtProperties
import com.contentplatform.auth.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("integration")
class ConfigurationPropertiesIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var jwtProperties: JwtProperties

    @Autowired
    lateinit var authProperties: AuthProperties

    @Autowired
    lateinit var gatewayProperties: GatewayProperties

    @Test
    fun `should load JwtProperties from application-test yml`() {
        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(jwtProperties.accessTokenTtl).isEqualTo(900)
            softly.assertThat(jwtProperties.refreshTokenTtl).isEqualTo(2592000)
            softly.assertThat(jwtProperties.issuer).isEqualTo("auth-service")
            softly.assertThat(jwtProperties.audience).isEqualTo("content-platform")
        }
    }

    @Test
    fun `should load AuthProperties from application-test yml`() {
        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(authProperties.verificationTokenTtl).isEqualTo(86400)
            softly.assertThat(authProperties.resetTokenTtl).isEqualTo(3600)
            softly.assertThat(authProperties.frontendUrl).isEqualTo("http://localhost:3000")
        }
    }

    @Test
    fun `should load GatewayProperties from application-test yml`() {
        assertThat(gatewayProperties.hmacSecret).isEqualTo("test-hmac-secret-for-integration-tests")
    }

    @Test
    fun `should autowire all configuration properties beans in Spring context`() {
        assertThat(jwtProperties).isNotNull
        assertThat(authProperties).isNotNull
        assertThat(gatewayProperties).isNotNull
    }
}
