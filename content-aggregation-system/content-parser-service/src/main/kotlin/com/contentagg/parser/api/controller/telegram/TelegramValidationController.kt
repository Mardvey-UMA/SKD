package com.contentagg.parser.api.controller.telegram

import com.contentagg.parser.api.model.telegram.validate.TelegramValidateRequest
import com.contentagg.parser.api.model.telegram.validate.TelegramValidateResponse
import com.contentagg.parser.configuration.properties.TelegramProperties
import com.contentagg.parser.exception.TelegramParseException
import com.contentagg.parser.integration.telegram.TelegramClientService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Internal endpoint — POST `/internal/telegram/validate` (Phase 2).
 *
 * Called by config-service's `CreateTelegramSourceProcessor` before inserting a TELEGRAM source
 * to verify the channel is reachable.
 *
 * Security: simple shared-secret header `X-Internal-Token`. No api-gateway route — docker-internal only.
 *
 * Endpoint is available regardless of `telegram.enabled` — when the TDLight client is absent we
 * return HTTP 503 with `error=tdlight_not_ready` so the caller can fail closed.
 */
@RestController
@RequestMapping("/internal/telegram")
class TelegramValidationController(
    telegramClientProvider: ObjectProvider<TelegramClientService>,
    private val telegramProperties: TelegramProperties,
) {

    companion object {
        private val log = LoggerFactory.getLogger(TelegramValidationController::class.java)
        private const val INTERNAL_TOKEN_HEADER = "X-Internal-Token"
    }

    private val telegramClient: TelegramClientService? = telegramClientProvider.ifAvailable

    @PostMapping("/validate")
    fun validate(
        @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) token: String?,
        @Valid @RequestBody request: TelegramValidateRequest,
    ): ResponseEntity<TelegramValidateResponse> {
        if (!isAuthorized(token)) {
            log.warn("Unauthorized /internal/telegram/validate call")
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val client = telegramClient
        if (client == null || !telegramProperties.enabled) {
            log.warn("TDLight client not ready — returning 503 tdlight_not_ready")
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(TelegramValidateResponse(accessible = false, reason = "tdlight_not_ready"))
        }

        val normalized = request.channelUsername.trim().removePrefix("@")
        return try {
            val chat = client.searchChannel(normalized)
            log.info("Validated channel '{}' -> chatId={} title={}", normalized, chat.id, chat.title)
            ResponseEntity.ok(
                TelegramValidateResponse(
                    accessible = true,
                    chatId = chat.id,
                    title = chat.title,
                )
            )
        } catch (ex: TelegramParseException) {
            log.info("Channel '{}' not accessible: {}", normalized, ex.message)
            ResponseEntity.ok(
                TelegramValidateResponse(
                    accessible = false,
                    reason = "channel_not_found",
                )
            )
        } catch (ex: Exception) {
            log.error("Unexpected error validating channel '{}': {}", normalized, ex.message, ex)
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(TelegramValidateResponse(accessible = false, reason = "internal_error"))
        }
    }

    private fun isAuthorized(token: String?): Boolean {
        val expected = telegramProperties.adminToken
        if (expected.isBlank()) return true
        return token == expected
    }
}
