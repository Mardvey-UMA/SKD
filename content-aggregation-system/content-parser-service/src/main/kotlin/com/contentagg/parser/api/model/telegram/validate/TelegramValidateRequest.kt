package com.contentagg.parser.api.model.telegram.validate

import jakarta.validation.constraints.NotBlank

/**
 * Internal-API request for `POST /internal/telegram/validate` (Phase 2).
 */
data class TelegramValidateRequest(
    @field:NotBlank(message = "channelUsername is required")
    val channelUsername: String,
)
