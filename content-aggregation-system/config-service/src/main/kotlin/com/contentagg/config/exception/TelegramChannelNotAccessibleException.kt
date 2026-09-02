package com.contentagg.config.exception

/**
 * Thrown when Telegram channel validation reports that the channel is not reachable.
 * Maps to HTTP 400.
 */
class TelegramChannelNotAccessibleException(
    val channelUsername: String,
    reason: String? = null,
) : BusinessException(
    ErrorCode.CHANNEL_NOT_ACCESSIBLE,
    reason ?: "Bot must be added to the channel first, or channel does not exist: $channelUsername"
)
