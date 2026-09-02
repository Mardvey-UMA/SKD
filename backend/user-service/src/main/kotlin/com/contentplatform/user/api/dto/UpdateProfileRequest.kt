package com.contentplatform.user.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class UpdateProfileRequest(
    @field:JsonProperty("display_name")
    val displayName: String?,
    @field:JsonProperty("avatar_url")
    val avatarUrl: String?
)
