package com.skd.userinteractions.api

import com.skd.userinteractions.api.model.interactions.InteractionBatchRequest
import com.skd.userinteractions.api.model.interactions.InteractionBatchResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader

interface InteractionApi {

    @PostMapping("/api/interactions/batch")
    fun submitBatch(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody request: InteractionBatchRequest
    ): ResponseEntity<InteractionBatchResponse>
}
