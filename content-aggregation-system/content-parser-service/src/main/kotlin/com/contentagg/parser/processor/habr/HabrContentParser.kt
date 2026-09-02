package com.contentagg.parser.processor.habr

import com.contentagg.parser.db.service.util.HabrSourceSubtype
import com.contentagg.parser.exception.HabrParseException
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import com.contentagg.parser.processor.habr.strategy.HabrParseStrategy
import com.contentagg.parser.processor.parser.ContentParser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Main Habr content parser that implements ContentParser interface.
 * Uses Strategy pattern to delegate to specific subtype parsers.
 *
 * Subtype determination from parameters JSONB:
 * - companyAlias present -> COMPANY strategy
 * - userLogin present    -> USER strategy
 * - hubAlias present     -> HUB strategy
 */
@Service
class HabrContentParser(
    private val strategies: List<HabrParseStrategy>,
) : ContentParser {

    companion object {
        private val log = LoggerFactory.getLogger(HabrContentParser::class.java)
        private const val SOURCE_TYPE = "HABR"
        private const val DEFAULT_MAX_ARTICLES = 50
    }

    override fun supports(sourceType: String): Boolean =
        SOURCE_TYPE.equals(sourceType, ignoreCase = true)

    override fun parse(sourceConfig: SourceConfigResponse): Int {
        log.info("Starting Habr parse for source: id={}, name={}", sourceConfig.id, sourceConfig.name)

        val subtype = determineSubtype(sourceConfig.parameters)
            ?: run {
                log.error("Cannot determine Habr subtype from parameters: {}", sourceConfig.parameters)
                throw HabrParseException(
                    "Invalid Habr source configuration: must have companyAlias, userLogin, or hubAlias in parameters"
                )
            }

        val strategy = findStrategy(subtype)
        val maxArticles = getMaxArticles(sourceConfig.parameters)

        log.info("Selected strategy: {} for source: {}", subtype, sourceConfig.id)

        return strategy.parse(sourceConfig, maxArticles)
    }

    /**
     * Determine Habr subtype from parameters JSONB.
     * Priority: companyAlias > userLogin > hubAlias
     */
    private fun determineSubtype(parameters: Map<String, Any>?): HabrSourceSubtype? {
        if (parameters.isNullOrEmpty()) return null

        return when {
            hasNonBlankValue(parameters, "companyAlias") -> HabrSourceSubtype.COMPANY
            hasNonBlankValue(parameters, "userLogin") -> HabrSourceSubtype.USER
            hasNonBlankValue(parameters, "hubAlias") -> HabrSourceSubtype.HUB
            else -> null
        }
    }

    /**
     * Check if map contains a non-blank string value for the key.
     */
    private fun hasNonBlankValue(parameters: Map<String, Any>, key: String): Boolean =
        parameters[key]?.toString()?.isNotBlank() == true

    /**
     * Find strategy for the given subtype.
     */
    private fun findStrategy(subtype: HabrSourceSubtype): HabrParseStrategy =
        strategies.firstOrNull { it.getSubtype() == subtype }
            ?: throw HabrParseException("No strategy found for Habr subtype: $subtype")

    /**
     * Get max articles limit from parameters.
     */
    private fun getMaxArticles(parameters: Map<String, Any>?): Int {
        if (parameters == null) return DEFAULT_MAX_ARTICLES
        val value = parameters["maxArticles"] ?: return DEFAULT_MAX_ARTICLES
        if (value is Number) return value.toInt()
        return try {
            value.toString().toInt()
        } catch (e: NumberFormatException) {
            log.warn("Invalid maxArticles value, using default: {}", DEFAULT_MAX_ARTICLES)
            DEFAULT_MAX_ARTICLES
        }
    }
}
