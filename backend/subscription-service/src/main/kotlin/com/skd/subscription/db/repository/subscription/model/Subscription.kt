package com.skd.subscription.db.repository.subscription.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("subscriptions")
data class Subscription(
    @Id
    val id: UUID? = null,
    @Column("user_id")
    val userId: UUID,
    @Column("plan_id")
    val planId: String,
    val tier: String,
    val status: String,
    @Column("started_at")
    val startedAt: Instant,
    @Column("expires_at")
    val expiresAt: Instant,
    @Column("auto_renew")
    val autoRenew: Boolean = true
)
