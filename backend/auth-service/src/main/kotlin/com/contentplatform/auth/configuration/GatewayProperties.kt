package com.contentplatform.auth.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "gateway")
class GatewayProperties {
    var hmacSecret: String = ""
}
