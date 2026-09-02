package com.contentagg.parser.db.repository.parsertask

import com.contentagg.parser.db.repository.parsertask.model.ParserTask
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository
import java.time.LocalDateTime
import java.util.UUID

interface ParserTaskRepository :
    CrudRepository<ParserTask, UUID>,
    PagingAndSortingRepository<ParserTask, UUID> {

    fun findByStatusAndScheduledAtLessThanEqual(status: String, scheduledAt: LocalDateTime): List<ParserTask>

    fun findBySourceIdOrderByScheduledAtDesc(sourceId: UUID): List<ParserTask>

    fun findBySourceTypeOrderByScheduledAtDesc(sourceType: String): List<ParserTask>

    fun countBySourceIdAndStatus(sourceId: UUID, status: String): Long

    fun countByStatusAndClaimedBy(status: String, claimedBy: String): Long
}
