package com.contentplatform.feed.infrastructure.cache

import com.contentplatform.feed.infrastructure.client.model.FeedItemDetail
import java.time.Instant
import java.util.UUID

data class CachedFeed(
    val items: List<UUID>,
    val itemsDetailed: List<FeedItemDetail>?,
    val cachedAt: Instant
)
