package com.contentagg.config.processor.telegram

import com.contentagg.config.enums.SourceType
import com.contentagg.config.exception.InvalidSourceException
import org.springframework.stereotype.Component

/**
 * Shared helper for Telegram source processing logic.
 * Centralizes URL building and parameter map construction
 * used by both CreateTelegramSourceProcessor and UpdateTelegramSourceProcessor.
 */
@Component
class TelegramSourceHelper {

    /**
     * Validate that the source type is TELEGRAM.
     */
    fun validateTelegramSourceType(sourceType: SourceType) {
        if (sourceType != SourceType.TELEGRAM) {
            throw InvalidSourceException(
                "Invalid Telegram source type: $sourceType. Must be: TELEGRAM"
            )
        }
    }

    /**
     * Build the Telegram channel URL from a channel username.
     * Strips leading '@' if present.
     */
    fun buildTelegramUrl(channelUsername: String): String {
        val cleanUsername = channelUsername.removePrefix("@")
        return "https://t.me/$cleanUsername"
    }

    /**
     * Build the parameters map for a Telegram source.
     * Strips leading '@' from channelUsername if present.
     */
    fun buildParametersMap(
        channelUsername: String,
        downloadMedia: Boolean?,
        maxMessages: Int?,
        maxMediaSizeMb: Int?,
        batchSize: Int?,
    ): Map<String, String> {
        return buildMap {
            put("channelUsername", channelUsername.removePrefix("@"))
            put("downloadMedia", (downloadMedia ?: true).toString())
            put("maxMessages", (maxMessages ?: 100).toString())
            put("maxMediaSizeMb", (maxMediaSizeMb ?: 50).toString())
            put("batchSize", (batchSize ?: 50).toString())
        }
    }
}
