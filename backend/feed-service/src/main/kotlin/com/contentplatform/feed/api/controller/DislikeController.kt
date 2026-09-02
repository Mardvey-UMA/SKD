package com.contentplatform.feed.api.controller

import com.contentplatform.feed.application.CollectionService
import com.contentplatform.feed.application.dto.CollectionPageResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/feed/dislikes")
class DislikeController(
    private val collectionService: CollectionService
) {

    @PostMapping("/{contentId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun addDislike(@PathVariable contentId: UUID): Map<String, String> {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        collectionService.addDislike(userId, contentId)
        return mapOf("content_id" to contentId.toString(), "action" to "disliked")
    }

    @DeleteMapping("/{contentId}")
    fun removeDislike(@PathVariable contentId: UUID): Map<String, String> {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        collectionService.removeDislike(userId, contentId)
        return mapOf("content_id" to contentId.toString(), "action" to "undisliked")
    }

    @GetMapping
    fun getDislikes(@RequestParam(required = false) cursor: String?): CollectionPageResponse {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        return collectionService.getDislikes(userId, cursor)
    }
}
