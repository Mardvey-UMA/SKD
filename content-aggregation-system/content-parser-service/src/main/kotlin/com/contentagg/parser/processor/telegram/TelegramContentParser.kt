package com.contentagg.parser.processor.telegram

import com.contentagg.parser.configuration.properties.TelegramProperties
import com.contentagg.parser.integration.telegram.TelegramAuthService
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import com.contentagg.parser.processor.parser.ContentParser
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(
    prefix = "telegram",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class TelegramContentParser(
    private val telegramParseProcessor: TelegramParseProcessor,
    private val telegramAuthService: TelegramAuthService,
    private val telegramProperties: TelegramProperties,
) : ContentParser {

    companion object {
        private val log = LoggerFactory.getLogger(TelegramContentParser::class.java)
        private const val SOURCE_TYPE = "TELEGRAM"
    }

    override fun supports(sourceType: String): Boolean =
        SOURCE_TYPE.equals(sourceType, ignoreCase = true)

    override fun parse(sourceConfig: SourceConfigResponse): Int {
        log.info("Starting Telegram parse for source: id={}, name={}", sourceConfig.id, sourceConfig.name)

        if (!telegramProperties.enabled) {
            log.warn("Telegram integration is disabled, skipping parse")
            return 0
        }

        if (!telegramAuthService.isAuthenticated()) {
            log.warn(
                "Telegram client is not authenticated (state={}), skipping parse for source: {}",
                telegramAuthService.getAuthState(),
                sourceConfig.id,
            )
            return 0
        }

        val context = TelegramParseContext.from(sourceConfig)
        log.info("Parsing Telegram channel: {} for source: {}", context.channelUsername, sourceConfig.id)

        return telegramParseProcessor.parseChannel(context)
    }
}
