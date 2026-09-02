package com.contentplatform.feed.application

import java.util.UUID

data class FeedItemLog(
    val requestId: UUID,
    val position: Int,
    val contentId: UUID,
    val rawContentId: UUID?,
    val finalScore: Float?,
    val scoringComponents: Map<String, Any?>?,
    val rerankScore: Float?,
    val filteredOutBy: String?
)
