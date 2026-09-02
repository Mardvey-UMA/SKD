package com.contentplatform.user.db.repository.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("outbox")
data class OutboxEventEntity(
    @Id val id: Long? = null,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val createdAt: Instant? = null,
    val publishedAt: Instant? = null
)
