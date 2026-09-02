package com.contentplatform.feed.api.controller

import com.contentplatform.feed.api.exception.NotFoundException
import com.contentplatform.feed.application.CollectionService
import com.contentplatform.feed.application.FeedItemLog
import com.contentplatform.feed.application.FeedLogService
import com.contentplatform.feed.application.FeedRequestLog
import com.contentplatform.feed.application.FeedService
import com.contentplatform.feed.application.dto.ContentStatusResponse
import com.contentplatform.feed.application.dto.FeedResponse
import com.contentplatform.feed.infrastructure.client.model.ContentBatchItem
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/feed")
class FeedController(
    private val feedService: FeedService,
    private val collectionService: CollectionService,
    @Autowired(required = false) private val feedLogService: FeedLogService? = null
) {

    companion object {
        private val log = LoggerFactory.getLogger(FeedController::class.java)
    }

    @GetMapping
    fun getFeed(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "false") refresh: Boolean,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): FeedResponse {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        val requestId = httpRequest.getHeader("X-Request-Id")
        val requestedAt = Instant.now()

        val feedResponse = feedService.getFeed(userId, cursor, refresh)

        // Set response headers
        if (requestId != null) {
            httpResponse.setHeader("X-Request-Id", requestId)
        }
        httpResponse.setHeader("X-Feed-Source", feedResponse.source)

        // Fire-and-forget logging — must not affect feed serving
        if (feedLogService != null) {
            try {
                val parsedRequestId = if (requestId != null) runCatching { UUID.fromString(requestId) }.getOrNull() else null
                if (parsedRequestId != null) {
                    val itemLogs: List<FeedItemLog> = if (feedResponse.itemsDetailed != null) {
                        val detailByContentId = feedResponse.itemsDetailed.associateBy { it.contentId }
                        feedResponse.items.mapIndexedNotNull { idx, contentItem ->
                            val contentId = runCatching { UUID.fromString(contentItem.id) }.getOrNull() ?: return@mapIndexedNotNull null
                            val detail = detailByContentId[contentId]
                            FeedItemLog(
                                requestId = parsedRequestId,
                                position = idx + 1,
                                contentId = contentId,
                                rawContentId = detail?.rawContentId,
                                finalScore = detail?.finalScore?.toFloat(),
                                scoringComponents = detail?.scoringComponents,
                                rerankScore = detail?.rerankScore?.toFloat(),
                                filteredOutBy = null
                            )
                        }
                    } else {
                        feedResponse.items.mapIndexedNotNull { idx, contentItem ->
                            val contentId = runCatching { UUID.fromString(contentItem.id) }.getOrNull() ?: return@mapIndexedNotNull null
                            FeedItemLog(
                                requestId = parsedRequestId,
                                position = idx + 1,
                                contentId = contentId,
                                rawContentId = null,
                                finalScore = null,
                                scoringComponents = null,
                                rerankScore = null,
                                filteredOutBy = null
                            )
                        }
                    }
                    val requestLog = FeedRequestLog(
                        requestId = parsedRequestId,
                        userId = userId,
                        requestedAt = requestedAt,
                        pageNumber = 0,
                        source = feedResponse.source,
                        countRequested = feedResponse.countRequested ?: feedResponse.items.size,
                        countReturned = feedResponse.items.size,
                        latencyMs = feedResponse.latencyMs,
                        latencyBreakdown = feedResponse.latencyBreakdown,
                        featureFlags = feedResponse.featureFlags,
                        abBucket = 0,
                        appVersion = httpRequest.getHeader("X-App-Version"),
                        deviceType = httpRequest.getHeader("X-Device-Type")
                    )
                    feedLogService.logFeedRequest(requestLog, itemLogs)
                }
            } catch (e: Exception) {
                log.warn("Feed request logging failed: {}", e.message, e)
            }
        }

        return feedResponse
    }

    @GetMapping("/content/{id}")
    fun getContentById(@PathVariable id: String): ContentBatchItem {
        return feedService.getContentById(id) ?: throw NotFoundException("Content not found: $id")
    }

    @GetMapping("/content/{contentId}/status")
    fun getContentStatus(@PathVariable contentId: UUID): ContentStatusResponse {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        return collectionService.getContentStatus(userId, contentId)
    }
}
