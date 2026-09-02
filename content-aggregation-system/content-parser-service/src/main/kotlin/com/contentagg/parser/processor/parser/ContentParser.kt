package com.contentagg.parser.processor.parser

import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse

/**
 * Interface for content parsers.
 * Implementations parse content from specific source types and save it to raw_content table.
 * Returns the number of records saved.
 */
interface ContentParser {

    /**
     * Check if this parser supports the given source type.
     */
    fun supports(sourceType: String): Boolean

    /**
     * Parse content from the given source configuration and persist to raw_content.
     *
     * @param sourceConfig the source configuration
     * @return count of raw_content records saved, or 0 if no new content found
     * @throws Exception if parsing fails
     */
    fun parse(sourceConfig: SourceConfigResponse): Int
}
