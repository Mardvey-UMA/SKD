package com.contentplatform.feed.db.entity

import java.util.UUID

data class FeedItemEntity(
    val requestId: UUID,
    val position: Short,
    val contentId: UUID,
    val rawContentId: UUID?,
    val finalScore: Float?,
    val scoringComponents: String?,
    val rerankScore: Float?,
    val filteredOutBy: String?
)
