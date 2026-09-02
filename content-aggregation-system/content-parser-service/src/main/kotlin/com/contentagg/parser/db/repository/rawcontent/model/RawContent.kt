package com.contentagg.parser.db.repository.rawcontent.model

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * Spring Data JDBC entity for raw_content table.
 * Immutable data class — use newInstance() for creating new unsaved entities.
 * JSONB columns stored as String, converted in service layer.
 * external_id column used for deduplication per source_type.
 */
@Table("raw_content")
data class RawContent(
    @Id
    val id: UUID? = null,
    val externalId: String,
    val sourceId: UUID,
    val sourceType: String,
    val rawData: String,
    val rawMedia: String?,
    val downloadedMedia: String?,
    val processingStatus: String,
    val isProcessedByDedup: Boolean = false,
    val isPublished: Boolean = false,
    val receivedAt: LocalDateTime,
    val processedAt: LocalDateTime?,
    val errorMessage: String?,
    val retryCount: Int?,
    @CreatedDate
    val createdAt: LocalDateTime? = null,
    @LastModifiedDate
    val updatedAt: LocalDateTime? = null,
) {
    companion object {
        /**
         * Create a new unsaved raw content record (id = null so Spring Data generates it).
         */
        @JvmStatic
        fun newInstance(
            externalId: String,
            sourceId: UUID,
            sourceType: String,
            rawData: String,
            rawMedia: String?,
            downloadedMedia: String?,
            processingStatus: String,
            receivedAt: LocalDateTime,
        ): RawContent = RawContent(
            id = null,
            externalId = externalId,
            sourceId = sourceId,
            sourceType = sourceType,
            rawData = rawData,
            rawMedia = rawMedia,
            downloadedMedia = downloadedMedia,
            processingStatus = processingStatus,
            isProcessedByDedup = false,
            isPublished = false,
            receivedAt = receivedAt,
            processedAt = null,
            errorMessage = null,
            retryCount = 0,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
    }

    /**
     * Return a copy with updated processing status.
     */
    fun withProcessingStatus(newStatus: String): RawContent =
        copy(processingStatus = newStatus)

    /**
     * Return a copy marked as failed with error message.
     */
    fun withFailed(error: String): RawContent = copy(
        processingStatus = "FAILED",
        errorMessage = error,
        retryCount = if (retryCount != null) retryCount + 1 else 1,
    )
}
