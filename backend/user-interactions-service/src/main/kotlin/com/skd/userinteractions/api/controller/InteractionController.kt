package com.skd.userinteractions.api.controller

import com.skd.userinteractions.api.InteractionApi
import com.skd.userinteractions.api.model.interactions.InteractionBatchRequest
import com.skd.userinteractions.api.model.interactions.InteractionBatchResponse
import com.skd.userinteractions.processor.InteractionProcessor
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class InteractionController(
    private val processor: InteractionProcessor
) : InteractionApi {

    companion object {
        private val log = LoggerFactory.getLogger(InteractionController::class.java)
    }

    override fun submitBatch(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody request: InteractionBatchRequest
    ): ResponseEntity<InteractionBatchResponse> {
        log.info("Received batch request for userId={}, events={}", userId, request.events.size)
        val response = processor.processBatch(UUID.fromString(userId), request)
        return ResponseEntity.status(202).body(response)
    }
}
