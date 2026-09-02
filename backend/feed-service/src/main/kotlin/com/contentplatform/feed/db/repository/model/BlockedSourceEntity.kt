package com.contentplatform.feed.db.repository.model

import java.time.Instant
import java.util.UUID

data class BlockedSourceEntity(
    val userId: UUID,
    val sourceId: UUID,
    val blockedAt: Instant
)
