package com.contentplatform.user.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class OnboardingRequest(
    @field:JsonProperty("categories")
    val categories: List<String>,
    @field:JsonProperty("source_content_ids")
    val sourceContentIds: List<String> = emptyList()
)
