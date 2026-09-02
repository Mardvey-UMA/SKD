package com.contentplatform.feed.db.repository.model

import com.contentplatform.feed.domain.SpaceColor
import java.time.Instant
import java.util.UUID

data class SpaceEntity(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val color: SpaceColor,
    val createdAt: Instant,
    val updatedAt: Instant
)
