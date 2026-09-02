package com.contentplatform.feed.db.repository.model

import java.time.Instant
import java.util.UUID

data class UserLikeEntity(
    val userId: UUID,
    val contentId: UUID,
    val createdAt: Instant
)
