package com.contentagg.parser.integration.rest.vcru.model

import com.fasterxml.jackson.annotation.JsonProperty

data class VcruArticleDto(
    @JsonProperty("id") val id: Long?,
    @JsonProperty("title") val title: String?,
    @JsonProperty("date") val date: Long?,
    @JsonProperty("dateModified") val dateModified: Long?,
    @JsonProperty("blocks") val blocks: List<VcruBlockDto>?,
    @JsonProperty("url") val url: String?,
    @JsonProperty("counters") val counters: VcruCountersDto?,
    @JsonProperty("author") val author: VcruAuthorDto?,
    @JsonProperty("subsite") val subsite: VcruArticleSubsiteDto?,
    @JsonProperty("isPublished") val isPublished: Boolean?,
    @JsonProperty("leadData") val leadData: VcruLeadDataDto?
)

data class VcruCountersDto(
    @JsonProperty("comments") val comments: Int?,
    @JsonProperty("favorites") val favorites: Int?,
    @JsonProperty("reposts") val reposts: Int?,
    @JsonProperty("views") val views: Int?,
    @JsonProperty("hits") val hits: Int?
)

data class VcruAuthorDto(
    @JsonProperty("id") val id: Long?,
    @JsonProperty("name") val name: String?,
    @JsonProperty("nickname") val nickname: String?,
    @JsonProperty("uri") val uri: String?
)

data class VcruArticleSubsiteDto(
    @JsonProperty("id") val id: Long?,
    @JsonProperty("name") val name: String?,
    @JsonProperty("urlName") val urlName: String?
)

data class VcruLeadDataDto(
    @JsonProperty("text") val text: String?
)
