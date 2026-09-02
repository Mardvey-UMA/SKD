package com.contentagg.parser.processor.habr.strategy

import com.contentagg.parser.db.service.util.HabrSourceSubtype
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import com.contentagg.parser.processor.habr.HabrParseProcessor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Strategy for parsing Habr company blog articles.
 * Derives company alias from parameters.companyAlias field.
 */
@Component
class HabrCompanyParseStrategy(
    private val parseProcessor: HabrParseProcessor,
) : HabrParseStrategy {

    companion object {
        private val log = LoggerFactory.getLogger(HabrCompanyParseStrategy::class.java)
        private const val COMPANY_ALIAS_KEY = "companyAlias"
    }

    override fun getSubtype(): HabrSourceSubtype = HabrSourceSubtype.COMPANY

    override fun parse(sourceConfig: SourceConfigResponse, maxArticles: Int): Int {
        val companyAlias = sourceConfig.getParameter(COMPANY_ALIAS_KEY, null)

        if (companyAlias.isNullOrBlank()) {
            log.warn("Company alias not found in parameters for source: {}", sourceConfig.id)
            return 0
        }

        log.info("Parsing Habr company articles: companyAlias={}, sourceId={}", companyAlias, sourceConfig.id)

        return parseProcessor.parseArticles(
            sourceConfig,
            HabrSourceSubtype.COMPANY,
            companyAlias,
            maxArticles,
        )
    }

    override fun getAlias(parameters: Map<String, String>?): String? =
        parameters?.get(COMPANY_ALIAS_KEY)
}
