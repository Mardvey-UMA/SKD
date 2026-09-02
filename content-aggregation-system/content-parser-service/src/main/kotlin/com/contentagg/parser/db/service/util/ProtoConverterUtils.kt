package com.contentagg.parser.db.service.util

import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * Utility object for converting between Kotlin and Protobuf types.
 */
object ProtoConverterUtils {

    /**
     * Get current Unix timestamp in milliseconds.
     */
    fun getCurrentTimestamp(): Long = Instant.now().toEpochMilli()

    /**
     * Convert Java Date to epoch millis timestamp.
     */
    fun toProtoTimestamp(date: Date?): Long {
        return date?.time ?: getCurrentTimestamp()
    }

    /**
     * Convert epoch millis timestamp back to Java Date.
     */
    fun fromProtoTimestamp(timestamp: Long): Date = Date(timestamp)

    /**
     * Check whether a string is a valid UUID.
     */
    fun isValidUUID(uuid: String?): Boolean {
        return try {
            UUID.fromString(uuid)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Generate a random UUID v4 as string.
     */
    fun generateUUID(): String = UUID.randomUUID().toString()
}
