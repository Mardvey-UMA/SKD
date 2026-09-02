package com.contentagg.parser.processor.telegram

import com.contentagg.parser.integration.telegram.TelegramAuthService
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "telegram",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class TelegramAuthProcessor(
    private val telegramAuthService: TelegramAuthService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(TelegramAuthProcessor::class.java)
    }

    fun getAuthStatus(): AuthStatus {
        return AuthStatus(
            state = telegramAuthService.getAuthState(),
            isAuthenticated = telegramAuthService.isAuthenticated(),
            passwordHint = telegramAuthService.getPasswordHint(),
        )
    }

    fun submitCode(code: String): AuthResult {
        MDC.put("operation", "telegramAuthCode")
        return try {
            log.info("Submitting Telegram auth code")
            val success = telegramAuthService.submitCode(code)
            AuthResult(
                success = success,
                message = if (success) "Code accepted" else "Code rejected",
                newState = telegramAuthService.getAuthState(),
            )
        } finally {
            MDC.clear()
        }
    }

    fun submitPassword(password: String): AuthResult {
        MDC.put("operation", "telegramAuthPassword")
        return try {
            log.info("Submitting Telegram auth password")
            val success = telegramAuthService.submitPassword(password)
            AuthResult(
                success = success,
                message = if (success) "Password accepted" else "Password rejected",
                newState = telegramAuthService.getAuthState(),
            )
        } finally {
            MDC.clear()
        }
    }

    data class AuthStatus(
        val state: String,
        val isAuthenticated: Boolean,
        val passwordHint: String,
    )

    data class AuthResult(
        val success: Boolean,
        val message: String,
        val newState: String,
    )
}
