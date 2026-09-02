package com.skd.userinteractions.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("user_interactions")
data class UserInteraction(
    @Id
    val id: Long?,
    @Column("user_id")
    val userId: UUID,
    @Column("content_id")
    val contentId: UUID,
    @Column("action_type")
    val actionType: String,
    @Column("duration_sec")
    val durationSec: Int?,
    @Column("client_ts")
    val clientTs: Instant,
    @Column("server_ts")
    val serverTs: Instant,
    @Column("feed_request_id")
    val feedRequestId: UUID? = null,
    @Column("position_in_feed")
    val positionInFeed: Short? = null,
    @Column("device_type")
    val deviceType: String? = null,
    @Column("app_version")
    val appVersion: String? = null,
    @Column("ab_bucket")
    val abBucket: Short = 0,
    @Column("scroll_depth")
    val scrollDepth: Float? = null,
    @Column("metadata")
    val metadata: Map<String, Any?>? = null
)
