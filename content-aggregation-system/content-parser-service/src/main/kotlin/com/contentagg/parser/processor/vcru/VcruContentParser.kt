package com.contentagg.parser.processor.vcru

import com.contentagg.parser.db.service.util.VcruSourceSubtype
import com.contentagg.parser.exception.VcruParseException
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import com.contentagg.parser.integration.rest.vcru.VcruApiClient
import com.contentagg.parser.processor.parser.ContentParser
import com.contentagg.parser.processor.vcru.strategy.VcruParseStrategy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Main VC.RU content parser that implements ContentParser interface.
 * Uses Strategy pattern to delegate to specific subtype parsers.
 *
 * Subtype determination via VC.RU API:
 * - GET /v2.7/subsite?uri={alias} -> subsite.type: 1=USER, 2=COMPANY
 */
@Service
class VcruContentParser(
    private val strategies: List<VcruParseStrategy>,
    private val vcruApiClient: VcruApiClient,
) : ContentParser {

    companion object {
        private val log = LoggerFactory.getLogger(VcruContentParser::class.java)
        private const val SOURCE_TYPE = "VCRU"
        private const val DEFAULT_MAX_ARTICLES = 50
        private const val ALIAS_KEY = "vcruAlias"
    }

    override fun supports(sourceType: String): Boolean =
        SOURCE_TYPE.equals(sourceType, ignoreCase = true)

    override fun parse(sourceConfig: SourceConfigResponse): Int {
        log.info("Starting VC.RU parse for source: id={}, name={}", sourceConfig.id, sourceConfig.name)

        val alias = getAlias(sourceConfig.parameters)
            ?: run {
                log.error("Cannot determine VC.RU alias from parameters: {}", sourceConfig.parameters)
                throw VcruParseException(
                    "Invalid VC.RU source configuration: vcruAlias missing from parameters"
                )
            }

        val subsite = vcruApiClient.getSubsiteInfo(alias)
        if (subsite == null) {
            log.warn("Skipping VC.RU source: subsite for alias={} unavailable (404 or empty response)", alias)
            return 0
        }

        val subtype = mapSubtype(subsite.type)
        val strategy = findStrategy(subtype)
        val maxArticles = getMaxArticles(sourceConfig.parameters)

        log.info("Resolved subtype: {} for alias: {}", subtype, alias)

        return strategy.parse(sourceConfig, maxArticles)
    }

    /**
     * Map VC.RU subsite.type to internal subtype enum.
     * 1=USER, 2=COMPANY
     */
    private fun mapSubtype(type: Int?): VcruSourceSubtype = when (type) {
        1 -> VcruSourceSubtype.USER
        2 -> VcruSourceSubtype.COMPANY
        else -> {
            log.warn("Unknown subsite type: {}, defaulting to USER", type)
            VcruSourceSubtype.USER
        }
    }

    /**
     * Extract alias from parameters map.
     */
    private fun getAlias(parameters: Map<String, Any>?): String? {
        if (parameters.isNullOrEmpty()) return null
        return parameters[ALIAS_KEY]?.toString()?.takeIf { it.isNotBlank() }
    }

    /**
     * Find strategy for the given subtype.
     */
    private fun findStrategy(subtype: VcruSourceSubtype): VcruParseStrategy =
        strategies.firstOrNull { it.getSubtype() == subtype }
            ?: throw VcruParseException("No strategy found for VC.RU subtype: $subtype")

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
