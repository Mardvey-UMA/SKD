package com.contentplatform.feed.api.controller

import com.contentplatform.feed.application.BlockedSourcesProxyService
import com.contentplatform.feed.application.dto.BlockedSourceRequest
import com.contentplatform.feed.application.dto.BlockedSourcesResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/feed/blocked-sources")
class BlockedSourcesController(
    private val proxyService: BlockedSourcesProxyService
) {

    @PostMapping
    fun block(@RequestBody request: BlockedSourceRequest): ResponseEntity<Void> {
        val userId = currentUserId()
        val sourceId = request.sourceId ?: throw IllegalArgumentException("source_id is required")
        proxyService.block(userId, sourceId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{sourceId}")
    fun unblock(@PathVariable sourceId: UUID): ResponseEntity<Void> {
        val userId = currentUserId()
        proxyService.unblock(userId, sourceId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping
    fun list(): BlockedSourcesResponse {
        val userId = currentUserId()
        return proxyService.list(userId)
    }

    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
}
