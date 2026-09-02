package com.contentagg.parser.db.service.rawcontent

import com.contentagg.parser.db.repository.rawcontent.RawContentRepository
import com.contentagg.parser.db.repository.rawcontent.model.RawContent
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class RawContentService(
    private val rawContentRepository: RawContentRepository,
    private val duplicateResolver: RawContentDuplicateResolver,
    private val objectMapper: ObjectMapper,
    @Value("\${parser.raw-content.max-raw-data-bytes:10485760}") private val maxRawDataBytes: Long,
    @Value("\${parser.raw-content.truncate-content-bytes:2097152}") private val truncateContentBytes: Int,
) {
    companion object {
        private val log = LoggerFactory.getLogger(RawContentService::class.java)
    }

    /**
     * Save raw content to database.
     * Caller is responsible for JSON serialization of rawData and downloadedMedia.
     * If rawData exceeds maxRawDataBytes, the content field is truncated to truncateContentBytes.
     *
     * This method is intentionally NOT @Transactional. The INSERT is delegated to
     * [RawContentDuplicateResolver.insertIsolated] which runs in its own REQUIRES_NEW
     * transaction. If the INSERT fails with a unique-constraint violation, that inner
     * transaction rolls back cleanly and the exception propagates here — without touching
     * any outer transaction and without leaving Postgres in an aborted-transaction state
     * (SQLSTATE 25P02). The subsequent lookup is then executed in a second fresh
     * REQUIRES_NEW transaction via [RawContentDuplicateResolver.findExisting].
     *
     * Returns null only if a DuplicateKeyException occurs AND the existing row cannot be
     * found (should be extremely rare in practice).
     */
    fun saveRawContent(
        externalId: String,
        sourceId: UUID,
        sourceType: String,
        rawData: String,
        rawMedia: String?,
        downloadedMedia: String?,
        processingStatus: String,
        receivedAt: LocalDateTime,
    ): UUID? {
        val effectiveRawData = applyInputSizeGuard(rawData, externalId)
        val rawContent = RawContent.newInstance(
            externalId = externalId,
            sourceId = sourceId,
            sourceType = sourceType,
            rawData = effectiveRawData,
            rawMedia = rawMedia,
            downloadedMedia = downloadedMedia,
            processingStatus = processingStatus,
            receivedAt = receivedAt,
        )
        return try {
            val saved = duplicateResolver.insertIsolated(rawContent)
            log.debug("Saved raw content: id={}, externalId={}", saved.id, externalId)
            saved.id
        } catch (e: Exception) {
            val isDuplicate = e is DuplicateKeyException
                || generateSequence(e.cause) { it.cause }.any { it is DuplicateKeyException }
            if (isDuplicate) {
                log.warn(
                    "duplicate raw_content skipped: sourceType={}, externalId={}",
                    sourceType,
                    externalId,
                )
                return duplicateResolver.findExisting(sourceType, externalId)?.id
            }
            throw e
        }
    }

    private fun applyInputSizeGuard(rawData: String, externalId: String): String {
        val rawBytes = rawData.toByteArray(Charsets.UTF_8)
        if (rawBytes.size <= maxRawDataBytes) return rawData
        log.warn(
            "rawData oversized: externalId={}, size={} bytes > maxRawDataBytes={} — truncating content to {} bytes",
            externalId, rawBytes.size, maxRawDataBytes, truncateContentBytes
        )
        return try {
            val dataMap = objectMapper.readValue(
                rawData,
                object : TypeReference<MutableMap<String, Any?>>() {}
            )
            val content = dataMap["content"] as? String
            if (content != null) {
                val truncated = content.toByteArray(Charsets.UTF_8)
                    .take(truncateContentBytes)
                    .toByteArray()
                    .toString(Charsets.UTF_8)
                dataMap["content"] = truncated
            }
            objectMapper.writeValueAsString(dataMap)
        } catch (e: Exception) {
            log.warn("Failed to parse rawData for truncation (externalId={}): {}", externalId, e.message)
            rawData
        }
    }

    /**
     * Check if raw content already exists for the given source type and external ID.
     * Used for deduplication in parse processors.
     */
    fun existsBySourceTypeAndExternalId(sourceType: String, externalId: String): Boolean =
        rawContentRepository.existsBySourceTypeAndExternalId(sourceType, externalId)

    /**
     * Update processing status for raw content.
     */
    @Transactional
    fun updateProcessingStatus(rawContentId: UUID, status: String, errorMessage: String?) {
        val updated = rawContentRepository.updateProcessingStatus(rawContentId, status, errorMessage)
        if (updated == 0) {
            log.warn("No raw content found with id: {}", rawContentId)
        }
    }

    /**
     * Get count of pending raw content for a source.
     */
    fun getPendingCount(sourceId: UUID): Int =
        rawContentRepository.countBySourceIdAndProcessingStatus(sourceId, "PENDING").toInt()

    /**
     * Delete a raw content record by ID.
     */
    @Transactional
    fun deleteById(id: UUID) {
        rawContentRepository.deleteById(id)
        log.debug("Deleted raw content record: id={}", id)
    }

    /**
     * Increment retry count for a failed processing attempt.
     */
    @Transactional
    fun incrementRetryCount(id: UUID, errorMessage: String?) {
        rawContentRepository.incrementRetryCount(id, errorMessage)
        log.debug("Incremented retry count for raw content: id={}", id)
    }
}
