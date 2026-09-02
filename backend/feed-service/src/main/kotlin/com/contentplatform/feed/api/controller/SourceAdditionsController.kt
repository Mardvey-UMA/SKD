package com.contentplatform.feed.api.controller

import com.contentplatform.feed.application.SourceAdditionsService
import com.contentplatform.feed.application.dto.SourceAdditionsPageResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/feed/my-additions")
class SourceAdditionsController(
    private val sourceAdditionsService: SourceAdditionsService
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?
    ): SourceAdditionsPageResponse {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        return sourceAdditionsService.listByUser(userId, cursor, limit)
    }
}
