package com.contentagg.parser.api.model.telegram.validate

/**
 * Internal-API response for `POST /internal/telegram/validate` (Phase 2).
 *
 * accessible=true  → chatId/title populated.
 * accessible=false → reason populated (e.g. "channel_not_found").
 */
data class TelegramValidateResponse(
    val accessible: Boolean,
    val chatId: Long? = null,
    val title: String? = null,
    val reason: String? = null,
)
