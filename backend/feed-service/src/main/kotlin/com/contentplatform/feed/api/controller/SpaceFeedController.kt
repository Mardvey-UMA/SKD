package com.contentplatform.feed.api.controller

import com.contentplatform.feed.application.SpaceFeedService
import com.contentplatform.feed.application.dto.FeedResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/feed/spaces")
class SpaceFeedController(
    private val spaceFeedService: SpaceFeedService
) {

    @GetMapping("/{id}/items")
    fun getItems(
        @PathVariable id: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?
    ): FeedResponse {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        return spaceFeedService.getSpaceFeed(userId, id, cursor, limit)
    }
}
