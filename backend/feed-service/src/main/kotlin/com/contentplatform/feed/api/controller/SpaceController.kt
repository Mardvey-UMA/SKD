package com.contentplatform.feed.api.controller

import com.contentplatform.feed.application.SpaceService
import com.contentplatform.feed.application.dto.CreateSpaceRequest
import com.contentplatform.feed.application.dto.SpaceListResponse
import com.contentplatform.feed.application.dto.SpaceResponse
import com.contentplatform.feed.application.dto.UpdateSpaceRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/feed/spaces")
class SpaceController(
    private val spaceService: SpaceService
) {

    @GetMapping
    fun list(): SpaceListResponse {
        val userId = currentUserId()
        return spaceService.list(userId)
    }

    @PostMapping
    fun create(@RequestBody request: CreateSpaceRequest): ResponseEntity<SpaceResponse> {
        val userId = currentUserId()
        val response = spaceService.create(userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): SpaceResponse {
        val userId = currentUserId()
        return spaceService.getById(userId, id)
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: UpdateSpaceRequest
    ): SpaceResponse {
        val userId = currentUserId()
        return spaceService.update(userId, id, request)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        val userId = currentUserId()
        spaceService.delete(userId, id)
        return ResponseEntity.noContent().build()
    }

    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
}
