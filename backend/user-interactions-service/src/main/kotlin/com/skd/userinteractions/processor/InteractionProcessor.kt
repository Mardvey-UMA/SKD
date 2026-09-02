package com.skd.userinteractions.processor

import com.skd.userinteractions.api.model.interactions.InteractionBatchRequest
import com.skd.userinteractions.api.model.interactions.InteractionBatchResponse
import com.skd.userinteractions.application.InteractionBuffer
import com.skd.userinteractions.domain.ActionType
import com.skd.userinteractions.domain.InteractionValidator
import com.skd.userinteractions.domain.UserInteraction
import com.skd.userinteractions.repository.UserInteractionRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class InteractionProcessor(
    private val validator: InteractionValidator,
    private val repository: UserInteractionRepository,
    private val buffer: InteractionBuffer,
    private val meterRegistry: MeterRegistry
) {

    companion object {
        private val log = LoggerFactory.getLogger(InteractionProcessor::class.java)
        private const val COUNTER_NAME = "interactions_batch_count"
        private const val TIMER_NAME = "interactions_batch_duration"
        private const val TAG_RESULT = "result"
        private const val TAG_ACCEPTED = "accepted"
        private const val TAG_REJECTED = "rejected"
    }

    private val batchTimer: Timer = Timer.builder(TIMER_NAME)
        .description("Duration of processBatch() execution")
        .register(meterRegistry)

    // Eagerly register counters so they appear in /actuator/prometheus even before any batch is processed
    private val acceptedCounter: Counter = Counter.builder(COUNTER_NAME)
        .tag(TAG_RESULT, TAG_ACCEPTED)
        .description("Number of accepted interaction events")
        .register(meterRegistry)

    private val rejectedCounter: Counter = Counter.builder(COUNTER_NAME)
        .tag(TAG_RESULT, TAG_REJECTED)
        .description("Number of rejected interaction events")
        .register(meterRegistry)

    fun processBatch(userId: UUID, request: InteractionBatchRequest): InteractionBatchResponse {
        return batchTimer.recordCallable {
            val result = validator.validate(request)

            if (result.accepted.isNotEmpty()) {
                val canonicalEvents = result.accepted.map { event ->
                    event.copy(actionType = ActionType.fromString(event.actionType).name)
                }
                val entities = canonicalEvents.map { event ->
                    UserInteraction(
                        id = null,
                        userId = userId,
                        contentId = UUID.fromString(event.contentId),
                        actionType = event.actionType,
                        durationSec = event.durationSec,
                        clientTs = event.timestamp,
                        serverTs = Instant.now(),
                        feedRequestId = event.feedRequestId?.let { UUID.fromString(it) },
                        positionInFeed = event.positionInFeed?.toShort(),
                        deviceType = event.deviceType,
                        appVersion = event.appVersion,
                        abBucket = (event.abBucket ?: 0).toShort(),
                        scrollDepth = event.scrollDepth?.toFloat(),
                        metadata = event.metadata
                    )
                }
                repository.saveAll(entities)
                buffer.addEvents(userId, canonicalEvents)
                log.info("Processed batch for userId={}, accepted={}, rejected={}", userId, result.accepted.size, result.rejectedCount)
            }

            acceptedCounter.increment(result.accepted.size.toDouble())
            if (result.rejectedCount > 0) {
                rejectedCounter.increment(result.rejectedCount.toDouble())
            }

            InteractionBatchResponse(
                accepted = result.accepted.size,
                rejected = result.rejectedCount
            )
        }!!
    }
}
