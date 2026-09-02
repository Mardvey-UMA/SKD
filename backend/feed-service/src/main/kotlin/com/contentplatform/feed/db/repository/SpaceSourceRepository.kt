package com.contentplatform.feed.db.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class SpaceSourceRepository(private val jdbcTemplate: JdbcTemplate) {

    fun insertAll(spaceId: UUID, sourceIds: List<UUID>) {
        if (sourceIds.isEmpty()) return
        val distinct = sourceIds.distinct()
        val sql = "INSERT INTO feed.space_sources (space_id, source_id) VALUES (?, ?) ON CONFLICT DO NOTHING"
        jdbcTemplate.batchUpdate(sql, distinct.map { arrayOf<Any>(spaceId, it) })
    }

    fun findSourceIds(spaceId: UUID): List<UUID> {
        return jdbcTemplate.query(
            "SELECT source_id FROM feed.space_sources WHERE space_id = ? ORDER BY added_at ASC",
            { rs, _ -> rs.getObject("source_id", UUID::class.java) },
            spaceId
        )
    }

    fun deleteAllBySpaceId(spaceId: UUID): Int {
        return jdbcTemplate.update(
            "DELETE FROM feed.space_sources WHERE space_id = ?",
            spaceId
        )
    }

    fun replaceAll(spaceId: UUID, sourceIds: List<UUID>) {
        deleteAllBySpaceId(spaceId)
        insertAll(spaceId, sourceIds)
    }

    fun countBySpaceId(spaceId: UUID): Int {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feed.space_sources WHERE space_id = ?",
            Int::class.java,
            spaceId
        ) ?: 0
    }
}
