package com.skd.subscription.db.repository.outbox.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("outbox")
data class OutboxEntry(
    @Id
    val id: Long? = null,
    @Column("aggregate_type")
    val aggregateType: String,
    @Column("aggregate_id")
    val aggregateId: String,
    @Column("event_type")
    val eventType: String,
    val payload: String,
    @Column("created_at")
    val createdAt: Instant,
    @Column("published_at")
    val publishedAt: Instant? = null
)
