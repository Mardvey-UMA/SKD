package com.contentplatform.feed.application.dto

data class SpaceListResponse(
    val items: List<SpaceResponse>,
    val count: Int,
    val limit: Int
)
