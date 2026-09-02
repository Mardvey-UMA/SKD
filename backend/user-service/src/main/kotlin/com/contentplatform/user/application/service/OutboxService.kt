package com.contentplatform.user.application.service

import com.contentplatform.user.db.repository.OutboxRepository
import com.contentplatform.user.db.repository.model.OutboxEventEntity
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class OutboxService(
    private val outboxRepository: OutboxRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(OutboxService::class.java)
    }

    fun createUserCreatedEvent(userId: UUID, email: String) {
        val payload = """{"event_type":"user.created","user_id":"$userId","email":"$email","timestamp":"${Instant.now()}"}"""
        val entity = OutboxEventEntity(
            aggregateType = "User",
            aggregateId = userId.toString(),
            eventType = "user.created",
            payload = payload,
            createdAt = Instant.now(),
            publishedAt = null
        )
        log.info("Creating user.created outbox event for userId={}", userId)
        outboxRepository.save(entity)
    }

    fun findUnpublished(limit: Int): List<OutboxEventEntity> {
        return outboxRepository.findUnpublished(limit)
    }

    fun markPublished(id: Long) {
        outboxRepository.markPublished(id, Instant.now())
    }
}
