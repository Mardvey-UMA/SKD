package com.contentplatform.auth.unit.configuration

import com.contentplatform.auth.configuration.AuthProperties
import com.contentplatform.auth.configuration.GatewayProperties
import com.contentplatform.auth.configuration.JwtProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@Tag("unit")
class ConfigurationPropertiesTest {

    @Nested
    @SpringBootTest(classes = [JwtPropertiesTestConfig::class])
    @TestPropertySource(properties = [
        "auth.jwt.access-token-ttl=900",
        "auth.jwt.refresh-token-ttl=2592000",
        "auth.jwt.issuer=test-issuer",
        "auth.jwt.audience=test-audience"
    ])
    inner class JwtPropertiesBindingTest {

        @Autowired
        lateinit var jwtProperties: JwtProperties

        @Test
        fun `should bind all JWT properties from configuration`() {
            SoftAssertions.assertSoftly { softly ->
                softly.assertThat(jwtProperties.accessTokenTtl).isEqualTo(900)
                softly.assertThat(jwtProperties.refreshTokenTtl).isEqualTo(2592000)
                softly.assertThat(jwtProperties.issuer).isEqualTo("test-issuer")
                softly.assertThat(jwtProperties.audience).isEqualTo("test-audience")
            }
        }

        @Test
        fun `should have access token TTL in seconds`() {
            assertThat(jwtProperties.accessTokenTtl).isEqualTo(900)
        }

        @Test
        fun `should have refresh token TTL in seconds`() {
            assertThat(jwtProperties.refreshTokenTtl).isEqualTo(2592000)
        }
    }

    @Nested
    @SpringBootTest(classes = [AuthPropertiesTestConfig::class])
    @TestPropertySource(properties = [
        "auth.verification-token-ttl=86400",
        "auth.reset-token-ttl=3600",
        "auth.frontend-url=http://localhost:3000"
    ])
    inner class AuthPropertiesBindingTest {

        @Autowired
        lateinit var authProperties: AuthProperties

        @Test
        fun `should bind all auth properties from configuration`() {
            SoftAssertions.assertSoftly { softly ->
                softly.assertThat(authProperties.verificationTokenTtl).isEqualTo(86400)
                softly.assertThat(authProperties.resetTokenTtl).isEqualTo(3600)
                softly.assertThat(authProperties.frontendUrl).isEqualTo("http://localhost:3000")
            }
        }

        @Test
        fun `should have verification token TTL in seconds`() {
            assertThat(authProperties.verificationTokenTtl).isEqualTo(86400)
        }

        @Test
        fun `should have reset token TTL in seconds`() {
            assertThat(authProperties.resetTokenTtl).isEqualTo(3600)
        }

        @Test
        fun `should have frontend URL for link generation`() {
            assertThat(authProperties.frontendUrl).isEqualTo("http://localhost:3000")
        }
    }

    @Nested
    @SpringBootTest(classes = [GatewayPropertiesTestConfig::class])
    @TestPropertySource(properties = [
        "gateway.hmac-secret=my-secret-key"
    ])
    inner class GatewayPropertiesBindingTest {

        @Autowired
        lateinit var gatewayProperties: GatewayProperties

        @Test
        fun `should bind HMAC secret from configuration`() {
            assertThat(gatewayProperties.hmacSecret).isEqualTo("my-secret-key")
        }

        @Test
        fun `should have non-empty HMAC secret`() {
            assertThat(gatewayProperties.hmacSecret).isNotBlank()
        }
    }
}

@EnableConfigurationProperties(JwtProperties::class)
class JwtPropertiesTestConfig

@EnableConfigurationProperties(AuthProperties::class)
class AuthPropertiesTestConfig

@EnableConfigurationProperties(GatewayProperties::class)
class GatewayPropertiesTestConfig
