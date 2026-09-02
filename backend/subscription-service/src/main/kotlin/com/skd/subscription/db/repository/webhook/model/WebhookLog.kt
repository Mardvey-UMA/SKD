package com.skd.subscription.db.repository.webhook.model

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("webhook_log")
data class WebhookLog(
    @Id
    @Column("external_event_id")
    @get:JvmName("getWebhookLogId")
    val externalEventId: String,
    @Column("event_type")
    val eventType: String,
    @Column("received_at")
    val receivedAt: Instant,
    val processed: Boolean = false,
    val payload: String
) : Persistable<String> {

    override fun getId(): String = externalEventId

    override fun isNew(): Boolean = true
}
