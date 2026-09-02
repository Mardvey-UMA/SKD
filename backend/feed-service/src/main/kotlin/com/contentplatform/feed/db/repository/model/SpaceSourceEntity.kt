package com.contentplatform.feed.db.repository.model

import java.time.Instant
import java.util.UUID

data class SpaceSourceEntity(
    val spaceId: UUID,
    val sourceId: UUID,
    val addedAt: Instant
)
