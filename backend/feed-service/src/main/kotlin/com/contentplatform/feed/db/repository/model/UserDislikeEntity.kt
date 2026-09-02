package com.contentplatform.feed.db.repository.model

import java.time.Instant
import java.util.UUID

data class UserDislikeEntity(
    val userId: UUID,
    val contentId: UUID,
    val createdAt: Instant
)
