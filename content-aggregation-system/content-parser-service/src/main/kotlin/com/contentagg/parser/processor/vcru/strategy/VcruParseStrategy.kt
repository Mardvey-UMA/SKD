package com.contentagg.parser.processor.vcru.strategy

import com.contentagg.parser.db.service.util.VcruSourceSubtype
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse

/**
 * Strategy interface for parsing VC.RU content.
 * Implementations handle specific VC.RU source subtypes (User, Company).
 */
interface VcruParseStrategy {

    fun getSubtype(): VcruSourceSubtype

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
