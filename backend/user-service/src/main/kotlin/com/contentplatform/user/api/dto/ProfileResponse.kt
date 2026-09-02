package com.contentplatform.user.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

data class ProfileResponse(
    @field:JsonProperty("id")
    val id: UUID,
    @field:JsonProperty("email")
    val email: String,
    @field:JsonProperty("display_name")
    val displayName: String?,
    @field:JsonProperty("avatar_url")
    val avatarUrl: String?,
    @field:JsonProperty("subscription_tier")
    val subscriptionTier: String,
    @field:JsonProperty("onboarding_completed")
    val onboardingCompleted: Boolean,
    @field:JsonProperty("created_at")
    val createdAt: Instant?
)
