package com.contentagg.parser.processor.telegram

import com.contentagg.parser.exception.TelegramParseException
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import java.util.UUID

data class TelegramParseContext(
    val sourceId: UUID,
    val sourceName: String?,
    val channelUsername: String,
    val maxMessages: Int,
    val downloadMedia: Boolean,
    val maxMediaSizeMb: Int,
    val batchSize: Int,
) {
    companion object {
        private const val DEFAULT_MAX_MESSAGES = 100
        private const val DEFAULT_BATCH_SIZE = 50
        private const val DEFAULT_MAX_MEDIA_SIZE_MB = 50

        fun from(config: SourceConfigResponse): TelegramParseContext {
            val params = config.parameters ?: emptyMap()

            val channelUsername = params["channelUsername"]?.toString()
                ?: throw TelegramParseException("channelUsername is required in Telegram source parameters")

            return TelegramParseContext(
                sourceId = UUID.fromString(config.id),
                sourceName = config.name,
                channelUsername = channelUsername,
                maxMessages = getIntParam(params, "maxMessages", DEFAULT_MAX_MESSAGES),
                downloadMedia = getBoolParam(params, "downloadMedia", true),
                maxMediaSizeMb = getIntParam(params, "maxMediaSizeMb", DEFAULT_MAX_MEDIA_SIZE_MB),
                batchSize = getIntParam(params, "batchSize", DEFAULT_BATCH_SIZE),
            )
        }

        private fun getIntParam(params: Map<String, Any>, key: String, default: Int): Int {
            val value = params[key] ?: return default
            return when (value) {
                is Number -> value.toInt()
                else -> value.toString().toIntOrNull() ?: default
            }
        }

        private fun getBoolParam(params: Map<String, Any>, key: String, default: Boolean): Boolean {
            val value = params[key] ?: return default
            return when (value) {
                is Boolean -> value
                else -> value.toString().toBooleanStrictOrNull() ?: default
            }
        }
    }
}
