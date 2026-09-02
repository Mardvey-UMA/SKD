package com.contentagg.parser.api.controller.telegram

import com.contentagg.parser.api.model.telegram.auth.AuthStatusResponse
import com.contentagg.parser.api.model.telegram.auth.SubmitCodeRequest
import com.contentagg.parser.api.model.telegram.auth.SubmitCodeResponse
import com.contentagg.parser.api.model.telegram.auth.SubmitPasswordRequest
import com.contentagg.parser.api.model.telegram.auth.SubmitPasswordResponse
import com.contentagg.parser.configuration.properties.TelegramProperties
import com.contentagg.parser.processor.telegram.TelegramAuthProcessor
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/telegram/auth")
@ConditionalOnProperty(
    prefix = "telegram",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class TelegramAuthController(
    private val telegramAuthProcessor: TelegramAuthProcessor,
    private val telegramProperties: TelegramProperties,
) {
    companion object {
        private val log = LoggerFactory.getLogger(TelegramAuthController::class.java)
        private const val ADMIN_TOKEN_HEADER = "X-Admin-Token"
    }

    @GetMapping("/status")
    fun getAuthStatus(): ResponseEntity<AuthStatusResponse> {
        log.debug("GET /api/v1/telegram/auth/status")
        val status = telegramAuthProcessor.getAuthStatus()
        return ResponseEntity.ok(
            AuthStatusResponse(
                state = status.state,
                isAuthenticated = status.isAuthenticated,
                passwordHint = status.passwordHint.ifBlank { null },
            )
        )
    }

    @PostMapping("/code")
    fun submitCode(
        @RequestHeader(ADMIN_TOKEN_HEADER, required = false) adminToken: String?,
        @Valid @RequestBody request: SubmitCodeRequest,
    ): ResponseEntity<SubmitCodeResponse> {
        if (!isAuthorized(adminToken)) {
            log.warn("Unauthorized attempt to submit auth code")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        log.info("POST /api/v1/telegram/auth/code")
        val result = telegramAuthProcessor.submitCode(request.code)
        return ResponseEntity.ok(
            SubmitCodeResponse(
                success = result.success,
                message = result.message,
                newState = result.newState,
            )
        )
    }

    @PostMapping("/password")
    fun submitPassword(
        @RequestHeader(ADMIN_TOKEN_HEADER, required = false) adminToken: String?,
        @Valid @RequestBody request: SubmitPasswordRequest,
    ): ResponseEntity<SubmitPasswordResponse> {
        if (!isAuthorized(adminToken)) {
            log.warn("Unauthorized attempt to submit auth password")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        log.info("POST /api/v1/telegram/auth/password")
        val result = telegramAuthProcessor.submitPassword(request.password)
        return ResponseEntity.ok(
            SubmitPasswordResponse(
                success = result.success,
                message = result.message,
                newState = result.newState,
            )
        )
    }

    private fun isAuthorized(token: String?): Boolean {
        val expectedToken = telegramProperties.adminToken
        if (expectedToken.isBlank()) return true // no token configured = no auth required
        return token == expectedToken
    }
}
