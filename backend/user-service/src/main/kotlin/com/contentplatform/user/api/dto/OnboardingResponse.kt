package com.contentplatform.user.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class OnboardingResponse(
    @field:JsonProperty("onboarding_completed")
    val onboardingCompleted: Boolean,
    @field:JsonProperty("message")
    val message: String,
    @field:JsonProperty("categories")
    val categories: List<String> = emptyList(),
)
