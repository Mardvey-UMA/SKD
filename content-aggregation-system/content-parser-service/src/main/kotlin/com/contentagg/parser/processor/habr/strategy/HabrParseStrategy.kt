package com.contentagg.parser.processor.habr.strategy

import com.contentagg.parser.db.service.util.HabrSourceSubtype
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse

/**
 * Strategy interface for parsing Habr content.
 * Implementations handle specific Habr source types (User, Company, Hub).
 */
interface HabrParseStrategy {

    fun getSubtype(): HabrSourceSubtype

    /**
     * Parse content from the given source configuration and persist to raw_content.
     *
     * @param sourceConfig the source configuration with parameters
     * @param maxArticles maximum number of articles to parse
     * @return count of records saved
     */
    fun parse(sourceConfig: SourceConfigResponse, maxArticles: Int): Int

    fun getAlias(parameters: Map<String, String>?): String?
}
