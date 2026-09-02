package com.contentplatform.feed.application.dto

import com.contentplatform.feed.infrastructure.client.model.ContentBatchItem

data class CollectionPageResponse(
    val items: List<ContentBatchItem>,
    val cursor: String?,
    val hasNext: Boolean
)
