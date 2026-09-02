package com.contentagg.aggregator.api.model.content

import com.contentagg.aggregator.db.repository.aggregatedcontent.model.SourceType
import java.time.Instant

data class ContentSearchRequest(
    val sourceType: SourceType? = null,
    val sourceId: String? = null,
    val fromDate: Instant? = null,
    val toDate: Instant? = null,
    val search: String? = null,
    val page: Int = 0,
    val size: Int = 20,
    val sortBy: String? = "publishedAt",
    val sortDirection: String? = "DESC"
)
