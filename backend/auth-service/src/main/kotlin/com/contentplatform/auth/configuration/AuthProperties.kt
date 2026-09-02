package com.contentplatform.auth.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "auth")
class AuthProperties {
    var verificationTokenTtl: Long = 86400
    var resetTokenTtl: Long = 3600
    var frontendUrl: String = ""
    var devMode: Boolean = false
    var verificationCodeTtlSeconds: Long = 600
    var verificationCodeMaxAttempts: Int = 5
    var verificationAttemptsWindowSeconds: Long = 600
    var verificationResendCooldownSeconds: Long = 60
}
