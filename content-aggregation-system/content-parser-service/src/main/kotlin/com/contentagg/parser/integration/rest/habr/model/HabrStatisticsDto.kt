package com.contentagg.parser.integration.rest.habr.model

import com.fasterxml.jackson.annotation.JsonProperty

data class HabrStatisticsDto(
    @JsonProperty("commentsCount") val commentsCount: Int?,
    @JsonProperty("favoritesCount") val favoritesCount: Int?,
    @JsonProperty("readingCount") val readingCount: Int?,
    @JsonProperty("score") val score: Int?,
    @JsonProperty("votesCount") val votesCount: Int?,
    @JsonProperty("votesCountPlus") val votesCountPlus: Int?,
    @JsonProperty("votesCountMinus") val votesCountMinus: Int?,
)
