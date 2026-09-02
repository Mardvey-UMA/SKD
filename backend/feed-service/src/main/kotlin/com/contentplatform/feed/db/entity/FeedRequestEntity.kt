package com.contentplatform.feed.db.entity

import java.time.Instant
import java.util.UUID

data class FeedRequestEntity(
    val requestId: UUID,
    val userId: UUID,
    val requestedAt: Instant,
    val pageNumber: Int,
    val source: String,
    val countRequested: Int,
    val countReturned: Int,
    val latencyMs: Int?,
    val latencyBreakdown: String?,
    val featureFlags: String?,
    val abBucket: Int,
    val appVersion: String?,
    val deviceType: String?
)
