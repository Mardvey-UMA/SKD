package com.contentplatform.feed.application.dto

data class ContentStatusResponse(
    val liked: Boolean,
    val disliked: Boolean,
    val bookmarked: Boolean
)
