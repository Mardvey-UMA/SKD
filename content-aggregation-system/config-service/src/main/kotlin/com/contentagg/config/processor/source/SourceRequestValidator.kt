package com.contentagg.config.processor.source

import com.contentagg.config.enums.SourceType
import com.contentagg.config.exception.InvalidSourceException
import org.springframework.stereotype.Component

/**
 * Validates source request parameters per source type.
 * Shared by CreateSourceProcessor and UpdateSourceProcessor.
 */
@Component
class SourceRequestValidator {

    /**
     * Validate that required parameters are present for the given source type.
     */
    fun validate(sourceType: SourceType, parameters: Map<String, String>?, url: String?) {
        if (parameters == null || parameters.isEmpty()) {
            throw InvalidSourceException("Source parameters cannot be empty")
        }

        when (sourceType) {
            SourceType.TELEGRAM -> validateTelegramParams(parameters)
            SourceType.HABR -> validateHabrParams(parameters)
            SourceType.VCRU -> validateVcruParams(url)
            SourceType.RSS -> validateRssParams(url)
        }
    }

    private fun validateTelegramParams(parameters: Map<String, String>) {
        if (!parameters.containsKey("channelUsername")) {
            throw InvalidSourceException("Telegram source requires 'channelUsername' parameter")
        }
    }

    private fun validateHabrParams(parameters: Map<String, String>) {
        val hasHabrIdentifier = parameters.containsKey("companyAlias") ||
            parameters.containsKey("userLogin") ||
            parameters.containsKey("hubAlias")
        if (!hasHabrIdentifier) {
            throw InvalidSourceException("Habr source requires 'companyAlias', 'userLogin', or 'hubAlias' parameter")
        }
    }

    private fun validateVcruParams(url: String?) {
        if (url.isNullOrBlank()) {
            throw InvalidSourceException("VC.ru source requires URL")
        }
    }

    private fun validateRssParams(url: String?) {
        if (url.isNullOrBlank()) {
            throw InvalidSourceException("RSS feed requires URL")
        }
    }
}
