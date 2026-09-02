package com.contentagg.parser.integration.rest.vcru.model

import com.fasterxml.jackson.annotation.JsonProperty

data class VcruSubsiteResponseDto(
    @JsonProperty("result") val result: VcruSubsiteDto?
)

data class VcruSubsiteDto(
    @JsonProperty("id") val id: Long?,
    @JsonProperty("uri") val uri: String?,
    @JsonProperty("url") val url: String?,
    @JsonProperty("type") val type: Int?,
    @JsonProperty("subtype") val subtype: String?,
    @JsonProperty("name") val name: String?,
    @JsonProperty("nickname") val nickname: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("counters") val counters: VcruSubsiteCountersDto?
)

data class VcruSubsiteCountersDto(
    @JsonProperty("subscribers") val subscribers: Int?,
    @JsonProperty("posts") val posts: Int?
)
