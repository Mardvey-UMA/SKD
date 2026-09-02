package com.contentplatform.feed.api.controller

import com.contentplatform.feed.application.SourceAdditionsService
import com.contentplatform.feed.application.dto.SourceAdditionsCountResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/internal/source-additions")
class InternalSourceAdditionsController(
    private val sourceAdditionsService: SourceAdditionsService
) {

    @GetMapping("/count")
    fun count(@RequestParam("user_id") userId: String): SourceAdditionsCountResponse {
        val parsed = try {
            UUID.fromString(userId)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid user_id")
        }
        val count = sourceAdditionsService.countByUser(parsed)
        return SourceAdditionsCountResponse(userId = parsed, count = count)
    }
}
