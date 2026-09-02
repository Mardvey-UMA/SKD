package com.skd.subscription.db.repository.payment.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("payments")
data class Payment(
    @Id
    val id: UUID? = null,
    @Column("user_id")
    val userId: UUID,
    @Column("plan_id")
    val planId: String,
    @Column("amount_kopecks")
    val amountKopecks: Int,
    val currency: String = "RUB",
    val status: String = "pending",
    @Column("external_id")
    val externalId: String? = null,
    @Column("external_status")
    val externalStatus: String? = null,
    @Column("confirmation_url")
    val confirmationUrl: String? = null,
    @Column("idempotency_key")
    val idempotencyKey: UUID,
    val metadata: String? = null,
    @Column("created_at")
    val createdAt: Instant,
    @Column("updated_at")
    val updatedAt: Instant
)
