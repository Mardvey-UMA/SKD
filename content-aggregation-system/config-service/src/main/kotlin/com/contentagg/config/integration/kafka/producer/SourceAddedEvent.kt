package com.contentagg.config.integration.kafka.producer

/**
 * JSON payload for the `source.added` topic (Phase 2).
 *
 * Intentionally JSON (not Protobuf) because this event crosses the boundary into the backend
 * platform cluster (feed-service consumes). Backend services use JSON for all Kafka topics.
 */
data class SourceAddedEvent(
    val sourceId: String,
    val userId: String,
    val sourceType: String,
    val name: String,
    val addedAt: String,
    val requestId: String?,
)
