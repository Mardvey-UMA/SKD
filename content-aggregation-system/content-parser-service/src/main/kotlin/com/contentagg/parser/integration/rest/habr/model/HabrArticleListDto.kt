package com.contentagg.parser.integration.rest.habr.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class HabrArticleListDto(
    @JsonProperty("pagesCount") val pagesCount: Int,
    @JsonProperty("publicationIds") val publicationIds: List<String>?,
    @JsonProperty("publicationRefs") val publicationRefs: Map<String, HabrArticleRefDto>?,
) {
    data class HabrArticleRefDto(
        @JsonProperty("id") val id: String?,
        @JsonProperty("timePublished") val timePublished: Instant?,
        @JsonProperty("titleHtml") val titleHtml: String?,
        @JsonProperty("author") val author: HabrAuthorDto?,
        @JsonProperty("statistics") val statistics: HabrStatisticsDto?,
        @JsonProperty("hubs") val hubs: List<HabrHubDto>?,
        @JsonProperty("leadData") val leadData: HabrArticleDto.HabrLeadDataDto?,
        @JsonProperty("postType") val postType: String?,
        @JsonProperty("readingTime") val readingTime: Int?,
    )
}
