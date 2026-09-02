package com.contentplatform.feed.application

import com.contentplatform.feed.db.entity.FeedItemEntity
import com.contentplatform.feed.db.entity.FeedRequestEntity
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class FeedLoggingMapper(private val objectMapper: ObjectMapper) {

    fun toEntity(log: FeedRequestLog): FeedRequestEntity {
        return FeedRequestEntity(
            requestId = log.requestId,
            userId = log.userId,
            requestedAt = log.requestedAt,
            pageNumber = log.pageNumber,
            source = log.source,
            countRequested = log.countRequested,
            countReturned = log.countReturned,
            latencyMs = log.latencyMs,
            latencyBreakdown = log.latencyBreakdown.toJson(),
            featureFlags = log.featureFlags.toJson(),
            abBucket = log.abBucket,
            appVersion = log.appVersion,
            deviceType = log.deviceType
        )
    }

    fun toEntity(log: FeedItemLog): FeedItemEntity {
        return FeedItemEntity(
            requestId = log.requestId,
            position = log.position.toShort(),
            contentId = log.contentId,
            rawContentId = log.rawContentId,
            finalScore = log.finalScore,
            scoringComponents = log.scoringComponents.toJson(),
            rerankScore = log.rerankScore,
            filteredOutBy = log.filteredOutBy
        )
    }

    private fun Map<String, Any?>?.toJson(): String? = this?.let { objectMapper.writeValueAsString(it) }
}
