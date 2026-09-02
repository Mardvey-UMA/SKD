package com.contentagg.parser.db.repository.rawcontent

import com.contentagg.parser.db.repository.rawcontent.model.RawContent
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository
import java.time.LocalDateTime
import java.util.UUID

interface RawContentRepository :
    CrudRepository<RawContent, UUID>,
    PagingAndSortingRepository<RawContent, UUID> {

    fun findByProcessingStatusAndReceivedAtLessThanEqual(status: String, receivedAt: LocalDateTime): List<RawContent>

    fun findByProcessingStatusAndRetryCountLessThanOrderByReceivedAtAsc(status: String, retryCount: Int): List<RawContent>

    fun findBySourceIdAndProcessingStatusOrderByReceivedAtDesc(sourceId: UUID, status: String): List<RawContent>

    fun findBySourceTypeAndProcessingStatusOrderByReceivedAtDesc(sourceType: String, status: String): List<RawContent>

    @Modifying
    @Query(
        "UPDATE raw_content SET processing_status = :newStatus, updated_at = NOW(), " +
            "processed_at = CASE WHEN :newStatus = 'COMPLETED' THEN NOW() ELSE processed_at END, " +
            "error_message = CASE WHEN :newStatus = 'FAILED' THEN :errorMessage ELSE error_message END, " +
            "retry_count = CASE WHEN :newStatus = 'FAILED' THEN retry_count + 1 ELSE retry_count END " +
            "WHERE id = :id"
    )
    fun updateProcessingStatus(id: UUID, newStatus: String, errorMessage: String?): Int

    @Modifying
    @Query("UPDATE raw_content SET retry_count = retry_count + 1, error_message = :errorMessage, updated_at = NOW() WHERE id = :id")
    fun incrementRetryCount(id: UUID, errorMessage: String?): Int

    fun countByProcessingStatus(status: String): Long

    fun countBySourceIdAndProcessingStatus(sourceId: UUID, status: String): Long

    @Query("SELECT EXISTS(SELECT 1 FROM raw_content WHERE source_type = :sourceType AND external_id = :externalId)")
    fun existsBySourceTypeAndExternalId(sourceType: String, externalId: String): Boolean

    @Query("SELECT * FROM raw_content WHERE source_type = :sourceType AND external_id = :externalId LIMIT 1")
    fun findBySourceTypeAndExternalId(sourceType: String, externalId: String): RawContent?

    @Modifying
    @Query("DELETE FROM raw_content WHERE processing_status = 'COMPLETED' AND processed_at < :olderThan")
    fun deleteOldCompleted(olderThan: LocalDateTime): Int

    @Modifying
    @Query("DELETE FROM raw_content WHERE id = ANY(CAST(:ids AS uuid[]))")
    fun deleteByIds(ids: Array<UUID>): Int

    @Modifying
    @Query("UPDATE raw_content SET is_published = true WHERE id = ANY(CAST(:ids AS uuid[]))")
    fun markPublished(ids: Array<UUID>): Int
}
