package com.skd.userinteractions.application

import com.skd.userinteractions.api.model.interactions.InteractionEvent
import com.skd.userinteractions.configuration.InteractionsProperties
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Component
class InteractionBuffer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val properties: InteractionsProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(InteractionBuffer::class.java)
        private const val TOPIC = "user.interactions.batch"
    }

    private val buffer = ConcurrentHashMap<UUID, ConcurrentLinkedQueue<InteractionEvent>>()

    fun addEvents(userId: UUID, events: List<InteractionEvent>) {
        val queue = buffer.computeIfAbsent(userId) { ConcurrentLinkedQueue() }
        queue.addAll(events)
        if (queue.size >= properties.batch.maxEventsPerUser) {
            drainAndPublish(userId)
        }
    }

    fun flushAll() {
        buffer.keys.forEach { userId -> drainAndPublish(userId) }
    }

    private fun drainAndPublish(userId: UUID) {
        val queue = buffer[userId] ?: return
        val drained = mutableListOf<InteractionEvent>()
        while (true) {
            val event = queue.poll() ?: break
            drained.add(event)
        }
        if (drained.isEmpty()) return
        val batch = InteractionBatch(
            userId = userId,
            interactions = drained,
            batchTs = Instant.now()
        )
        log.debug("Publishing batch for userId={}, size={}", userId, drained.size)
        kafkaTemplate.send(TOPIC, userId.toString(), batch)
    }

    @Scheduled(fixedDelayString = "\${interactions.batch.flush-interval-ms}")
    fun scheduledFlush() {
        log.debug("Scheduled flush triggered")
        flushAll()
    }

    @PreDestroy
    fun shutdown() {
        log.info("Shutting down InteractionBuffer, flushing remaining events")
        flushAll()
    }
}
