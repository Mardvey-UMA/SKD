package com.contentagg.parser.db.repository.parsertask.model

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * Spring Data JDBC entity for parser_tasks table.
 * Immutable data class — use newInstance() for creating new unsaved entities.
 */
@Table("parser_tasks")
data class ParserTask(
    @Id
    val id: UUID? = null,
    val sourceId: UUID,
    val sourceType: String,
    val taskType: String,
    val status: String,
    val scheduledAt: LocalDateTime,
    val startedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val errorMessage: String?,
    val retryCount: Int?,
    val claimedBy: String? = null,
    val claimedAt: LocalDateTime? = null,
    @CreatedDate
    val createdAt: LocalDateTime? = null,
    @LastModifiedDate
    val updatedAt: LocalDateTime? = null,
) {
    companion object {
        /**
         * Create a new unsaved parser task (id = null so Spring Data generates it).
         */
        @JvmStatic
        fun newInstance(
            sourceId: UUID,
            sourceType: String,
            taskType: String,
            status: String,
            scheduledAt: LocalDateTime,
            startedAt: LocalDateTime,
        ): ParserTask = ParserTask(
            id = null,
            sourceId = sourceId,
            sourceType = sourceType,
            taskType = taskType,
            status = status,
            scheduledAt = scheduledAt,
            startedAt = startedAt,
            completedAt = null,
            errorMessage = null,
            retryCount = 0,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
    }

    /**
     * Return a copy with updated status.
     */
    fun withStatus(newStatus: String): ParserTask = copy(status = newStatus)

    /**
     * Return a copy marked as completed.
     */
    fun withCompleted(completedAt: LocalDateTime): ParserTask = copy(
        status = "COMPLETED",
        completedAt = completedAt,
    )

    /**
     * Return a copy marked as failed.
     */
    fun withFailed(errorMessage: String): ParserTask = copy(
        status = "FAILED",
        errorMessage = errorMessage,
    )
}

/**
 * Return a copy claimed by the given instance and transitioned to RUNNING.
 */
fun ParserTask.withClaimed(instanceId: String, now: LocalDateTime): ParserTask =
    copy(
        status = "RUNNING",
        claimedBy = instanceId,
        claimedAt = now,
        startedAt = now,
    )

/**
 * Return a copy released back to PENDING (cleared claim fields).
 */
fun ParserTask.withReleased(): ParserTask =
    copy(
        status = "PENDING",
        claimedBy = null,
        claimedAt = null,
        startedAt = null,
    )
