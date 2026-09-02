package com.contentplatform.feed.db.repository

import com.contentplatform.feed.db.repository.model.SourceAdditionEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class SourceAdditionsRepository(private val jdbcTemplate: JdbcTemplate) {

    fun upsert(
        userId: UUID,
        sourceId: UUID,
        sourceType: String,
        sourceName: String,
        addedAt: Instant
    ): Int {
        return jdbcTemplate.update(
            """
            INSERT INTO feed.source_additions
                (user_id, source_id, source_type, source_name, added_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (user_id, source_id) DO NOTHING
            """.trimIndent(),
            userId, sourceId, sourceType, sourceName, Timestamp.from(addedAt)
        )
    }

    fun findByUserId(userId: UUID, limit: Int, offset: Int): List<SourceAdditionEntity> {
        return jdbcTemplate.query(
            "SELECT user_id, source_id, source_type, source_name, added_at FROM feed.source_additions " +
                "WHERE user_id = ? ORDER BY added_at DESC LIMIT ? OFFSET ?",
            { rs: ResultSet, _ -> mapRow(rs) },
            userId, limit, offset
        )
    }

    fun countByUserId(userId: UUID): Int {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feed.source_additions WHERE user_id = ?",
            Int::class.java,
            userId
        ) ?: 0
    }

    private fun mapRow(rs: ResultSet): SourceAdditionEntity {
        return SourceAdditionEntity(
            userId = rs.getObject("user_id", UUID::class.java),
            sourceId = rs.getObject("source_id", UUID::class.java),
            sourceType = rs.getString("source_type"),
            sourceName = rs.getString("source_name"),
            addedAt = rs.getTimestamp("added_at").toInstant()
        )
    }
}
