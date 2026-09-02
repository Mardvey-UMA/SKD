package com.contentagg.parser.integration.rest.configservice.model

import com.contentagg.parser.db.service.util.SourceType
import com.fasterxml.jackson.annotation.JsonProperty

data class HabrSourceConfig(
    @JsonProperty("companyAlias") val companyAlias: String?,
    @JsonProperty("userLogin") val userLogin: String?,
    @JsonProperty("hubAlias") val hubAlias: String?,
    @JsonProperty("parseComments") val parseComments: Boolean?,
    @JsonProperty("parseImages") val parseImages: Boolean?,
    @JsonProperty("maxArticles") val maxArticles: Int?,
    @JsonProperty("minRating") val minRating: Int?,
    @JsonProperty("hubsFilter") val hubsFilter: List<String>?,
    @JsonProperty("tagsFilter") val tagsFilter: List<String>?,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromSourceConfig(sourceConfig: SourceConfigResponse): HabrSourceConfig {
            val params = sourceConfig.parameters
                ?: throw IllegalArgumentException("Source parameters are required")
            if (params.isEmpty()) {
                throw IllegalArgumentException("Source parameters are required")
            }

            return HabrSourceConfig(
                companyAlias = params["companyAlias"] as? String,
                userLogin = params["userLogin"] as? String,
                hubAlias = params["hubAlias"] as? String,
                parseComments = (params.getOrDefault("parseComments", false)) as? Boolean ?: false,
                parseImages = (params.getOrDefault("parseImages", true)) as? Boolean ?: true,
                maxArticles = (params.getOrDefault("maxArticles", 100)) as? Int ?: 100,
                minRating = params["minRating"] as? Int,
                hubsFilter = params["hubsFilter"] as? List<String>,
                tagsFilter = params["tagsFilter"] as? List<String>,
            )
        }
    }

    fun getAlias(sourceType: SourceType): String {
        if (sourceType != SourceType.HABR) {
            throw IllegalArgumentException("Unexpected source type: $sourceType")
        }
        return companyAlias
            ?: userLogin
            ?: hubAlias
            ?: throw IllegalArgumentException("No alias set in HabrSourceConfig")
    }

    fun validate(sourceType: SourceType) {
        if (sourceType != SourceType.HABR) {
            throw IllegalArgumentException("Unexpected source type: $sourceType")
        }

        if (companyAlias == null && userLogin == null && hubAlias == null) {
            throw IllegalArgumentException(
                "Missing required parameter: one of companyAlias, userLogin, or hubAlias must be set"
            )
        }

        if (maxArticles != null && maxArticles < 1) {
            throw IllegalArgumentException("maxArticles must be at least 1")
        }

        if (maxArticles != null && maxArticles > 1000) {
            throw IllegalArgumentException("maxArticles cannot exceed 1000")
        }
    }
}
