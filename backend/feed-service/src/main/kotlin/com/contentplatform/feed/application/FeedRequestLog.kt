package com.contentplatform.feed.application

import java.time.Instant
import java.util.UUID

data class FeedRequestLog(
    val requestId: UUID,
    val userId: UUID,
    val requestedAt: Instant,
    val pageNumber: Int,
    val source: String,
    val countRequested: Int,
    val countReturned: Int,
    val latencyMs: Int?,
    val latencyBreakdown: Map<String, Any?>?,
    val featureFlags: Map<String, Any?>?,
    val abBucket: Int,
    val appVersion: String?,
    val deviceType: String?
)
