package com.contentagg.parser.processor.habr.strategy

import com.contentagg.parser.db.service.util.HabrSourceSubtype
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import com.contentagg.parser.processor.habr.HabrParseProcessor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Strategy for parsing Habr user articles.
 * Derives user login from parameters.userLogin field.
 */
@Component
class HabrUserParseStrategy(
    private val parseProcessor: HabrParseProcessor,
) : HabrParseStrategy {

    companion object {
        private val log = LoggerFactory.getLogger(HabrUserParseStrategy::class.java)
        private const val USER_LOGIN_KEY = "userLogin"
    }

    override fun getSubtype(): HabrSourceSubtype = HabrSourceSubtype.USER

    override fun parse(sourceConfig: SourceConfigResponse, maxArticles: Int): Int {
        val userLogin = sourceConfig.getParameter(USER_LOGIN_KEY, null)

        if (userLogin.isNullOrBlank()) {
            log.warn("User login not found in parameters for source: {}", sourceConfig.id)
            return 0
        }

        log.info("Parsing Habr user articles: userLogin={}, sourceId={}", userLogin, sourceConfig.id)

        return parseProcessor.parseArticles(
            sourceConfig,
            HabrSourceSubtype.USER,
            userLogin,
            maxArticles,
        )
    }

    override fun getAlias(parameters: Map<String, String>?): String? =
        parameters?.get(USER_LOGIN_KEY)
}
