package com.contentplatform.feed.db.repository.model

import java.time.Instant
import java.util.UUID

data class SourceAdditionEntity(
    val userId: UUID,
    val sourceId: UUID,
    val sourceType: String,
    val sourceName: String,
    val addedAt: Instant
)
