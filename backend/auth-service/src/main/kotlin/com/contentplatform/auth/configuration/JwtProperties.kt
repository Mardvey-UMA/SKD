package com.contentplatform.auth.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "auth.jwt")
class JwtProperties {
    var accessTokenTtl: Long = 900
    var refreshTokenTtl: Long = 2592000
    var issuer: String = ""
    var audience: String = ""
}
