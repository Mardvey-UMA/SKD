package com.skd.userinteractions.domain

import com.skd.userinteractions.api.model.interactions.InteractionBatchRequest
import com.skd.userinteractions.api.model.interactions.InteractionEvent
import com.skd.userinteractions.configuration.InteractionsProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class InteractionValidator(
    private val properties: InteractionsProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(InteractionValidator::class.java)
    }

    fun validate(request: InteractionBatchRequest): ValidationResult {
        if (request.events.isEmpty()) {
            throw IllegalArgumentException("Batch must not be empty")
        }
        if (request.events.size > properties.validation.maxEventsPerBatch) {
            throw IllegalArgumentException(
                "Batch size ${request.events.size} exceeds maximum of ${properties.validation.maxEventsPerBatch} (100)"
            )
        }

        val now = Instant.now()
        val maxFutureInstant = now.plusSeconds(properties.validation.maxTimestampFutureSec.toLong())
        val minPastInstant = now.minusSeconds(properties.validation.maxTimestampAgeHours.toLong() * 3600)

        val accepted = mutableListOf<InteractionEvent>()
        var rejectedCount = 0

        for (event in request.events) {
            if (isValid(event, maxFutureInstant, minPastInstant)) {
                accepted.add(event)
            } else {
                rejectedCount++
                log.debug("Rejected event: contentId={}, actionType={}", event.contentId, event.actionType)
            }
        }

        return ValidationResult(accepted = accepted, rejectedCount = rejectedCount)
    }

    private fun isValid(event: InteractionEvent, maxFuture: Instant, minPast: Instant): Boolean {
        // Validate action type
        try {
            ActionType.fromString(event.actionType)
        } catch (e: IllegalArgumentException) {
            return false
        }

        // Validate content ID as UUID
        try {
            UUID.fromString(event.contentId)
        } catch (e: IllegalArgumentException) {
            return false
        }

        // Validate duration
        if (event.durationSec != null && event.durationSec < 0) {
            return false
        }

        // Validate timestamp
        if (event.timestamp.isAfter(maxFuture)) {
            return false
        }
        if (event.timestamp.isBefore(minPast)) {
            return false
        }

        return true
    }
}
