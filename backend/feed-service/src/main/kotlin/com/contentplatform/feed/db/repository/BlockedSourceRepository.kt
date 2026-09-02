package com.contentplatform.feed.db.repository

import com.contentplatform.feed.db.repository.model.BlockedSourceEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class BlockedSourceWithMetadata(
    val userId: UUID,
    val sourceId: UUID,
    val blockedAt: Instant,
    val sourceType: String?,
    val sourceName: String?
)

@Repository
class BlockedSourceRepository(private val jdbcTemplate: JdbcTemplate) {

    fun insert(userId: UUID, sourceId: UUID) {
        jdbcTemplate.update(
            """
            INSERT INTO feed.blocked_sources (user_id, source_id)
            VALUES (?, ?)
            ON CONFLICT (user_id, source_id) DO NOTHING
            """.trimIndent(),
            userId, sourceId
        )
    }

    fun delete(userId: UUID, sourceId: UUID) {
        jdbcTemplate.update(
            "DELETE FROM feed.blocked_sources WHERE user_id = ? AND source_id = ?",
            userId, sourceId
        )
    }

    fun findByUserId(userId: UUID): List<BlockedSourceEntity> {
        return jdbcTemplate.query(
            """
            SELECT user_id, source_id, blocked_at
              FROM feed.blocked_sources
             WHERE user_id = ?
             ORDER BY blocked_at DESC
            """.trimIndent(),
            { rs: ResultSet, _ -> mapRow(rs) },
            userId
        )
    }

    fun findBlockedSourceIds(userId: UUID): Set<UUID> {
        return jdbcTemplate.queryForList(
            "SELECT source_id FROM feed.blocked_sources WHERE user_id = ?",
            UUID::class.java,
            userId
        ).toSet()
    }

    fun countByUserId(userId: UUID): Long {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feed.blocked_sources WHERE user_id = ?",
            Long::class.java,
            userId
        ) ?: 0L
    }

    fun findByUserIdWithMetadata(userId: UUID): List<BlockedSourceWithMetadata> {
        return jdbcTemplate.query(
            """
            SELECT bs.user_id, bs.source_id, bs.blocked_at,
                   sa.source_type, sa.source_name
              FROM feed.blocked_sources bs
         LEFT JOIN feed.source_additions sa
                ON sa.user_id = bs.user_id AND sa.source_id = bs.source_id
             WHERE bs.user_id = ?
             ORDER BY bs.blocked_at DESC
            """.trimIndent(),
            { rs: ResultSet, _ ->
                BlockedSourceWithMetadata(
                    userId = rs.getObject("user_id", UUID::class.java),
                    sourceId = rs.getObject("source_id", UUID::class.java),
                    blockedAt = rs.getTimestamp("blocked_at").toInstant(),
                    sourceType = rs.getString("source_type"),
                    sourceName = rs.getString("source_name")
                )
            },
            userId
        )
    }

    private fun mapRow(rs: ResultSet): BlockedSourceEntity {
        return BlockedSourceEntity(
            userId = rs.getObject("user_id", UUID::class.java),
            sourceId = rs.getObject("source_id", UUID::class.java),
            blockedAt = rs.getTimestamp("blocked_at").toInstant()
        )
    }
}
