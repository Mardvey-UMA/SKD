package com.contentplatform.feed.application

import com.contentplatform.feed.application.dto.SourceAdditionResponse
import com.contentplatform.feed.application.dto.SourceAdditionsPageResponse
import com.contentplatform.feed.db.repository.SourceAdditionsRepository
import com.contentplatform.feed.infrastructure.cache.CursorCodec
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class SourceAdditionsService(
    private val repository: SourceAdditionsRepository,
    private val cursorCodec: CursorCodec,
    @Value("\${feed.my-additions.page-size:20}") private val defaultPageSize: Int = 20,
    @Value("\${feed.my-additions.max-limit:50}") private val maxLimit: Int = 50
) {

    companion object {
        private val log = LoggerFactory.getLogger(SourceAdditionsService::class.java)
    }

    fun recordAddition(
        userId: UUID,
        sourceId: UUID,
        sourceType: String,
        sourceName: String,
        addedAt: Instant
    ) {
        val inserted = repository.upsert(userId, sourceId, sourceType, sourceName, addedAt)
        if (inserted == 0) {
            log.debug("source.added event already recorded for userId={} sourceId={}", userId, sourceId)
        }
    }

    fun listByUser(userId: UUID, cursor: String?, limit: Int?): SourceAdditionsPageResponse {
        val pageSize = resolveLimit(limit)
        val offset = cursorCodec.decode(cursor)
        val rows = repository.findByUserId(userId, pageSize + 1, offset)
        val hasNext = rows.size > pageSize
        val pageRows = if (hasNext) rows.dropLast(1) else rows
        val items = pageRows.map {
            SourceAdditionResponse(
                sourceId = it.sourceId,
                sourceType = it.sourceType,
                sourceName = it.sourceName,
                addedAt = it.addedAt
            )
        }
        val nextCursor = if (hasNext) cursorCodec.encode(offset + pageSize) else null
        return SourceAdditionsPageResponse(items = items, cursor = nextCursor, hasNext = hasNext)
    }

    fun countByUser(userId: UUID): Int = repository.countByUserId(userId)

    private fun resolveLimit(limit: Int?): Int {
        val target = limit ?: defaultPageSize
        if (target < 1 || target > maxLimit) {
            throw IllegalArgumentException("Invalid limit: must be between 1 and $maxLimit")
        }
        return target
    }
}
