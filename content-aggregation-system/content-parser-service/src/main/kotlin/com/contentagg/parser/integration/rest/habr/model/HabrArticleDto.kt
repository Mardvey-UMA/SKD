package com.contentagg.parser.integration.rest.habr.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class HabrArticleDto(
    @JsonProperty("id") val id: String?,
    @JsonProperty("timePublished") val timePublished: Instant?,
    @JsonProperty("isCorporative") val isCorporative: Boolean?,
    @JsonProperty("lang") val lang: String?,
    @JsonProperty("titleHtml") val titleHtml: String?,
    @JsonProperty("leadData") val leadData: HabrLeadDataDto?,
    @JsonProperty("postType") val postType: String?,
    @JsonProperty("author") val author: HabrAuthorDto?,
    @JsonProperty("statistics") val statistics: HabrStatisticsDto?,
    @JsonProperty("hubs") val hubs: List<HabrHubDto>?,
    @JsonProperty("flows") val flows: List<HabrFlowDto>?,
    @JsonProperty("textHtml") val textHtml: String?,
    @JsonProperty("tags") val tags: List<HabrTagDto>?,
    @JsonProperty("metadata") val metadata: HabrMetadataDto?,
    @JsonProperty("readingTime") val readingTime: Int?,
    @JsonProperty("complexity") val complexity: String?,
    @JsonProperty("format") val format: String?,
    @JsonProperty("status") val status: String?,
) {
    data class HabrLeadDataDto(
        @JsonProperty("textHtml") val textHtml: String?,
        @JsonProperty("imageUrl") val imageUrl: String?,
        @JsonProperty("image") val image: HabrImageDataDto?,
    )

    data class HabrImageDataDto(
        @JsonProperty("url") val url: String?,
        @JsonProperty("fit") val fit: String?,
        @JsonProperty("positionY") val positionY: Int?,
        @JsonProperty("positionX") val positionX: Int?,
    )

    data class HabrFlowDto(
        @JsonProperty("id") val id: String?,
        @JsonProperty("alias") val alias: String?,
        @JsonProperty("title") val title: String?,
    )

    data class HabrMetadataDto(
        @JsonProperty("stylesUrls") val stylesUrls: List<String>?,
        @JsonProperty("scriptUrls") val scriptUrls: List<String>?,
        @JsonProperty("shareImageUrl") val shareImageUrl: String?,
        @JsonProperty("metaDescription") val metaDescription: String?,
    )
}
