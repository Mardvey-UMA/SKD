package com.contentplatform.feed.db.repository

import com.contentplatform.feed.db.repository.model.SpaceEntity
import com.contentplatform.feed.domain.SpaceColor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class SpaceRepository(private val jdbcTemplate: JdbcTemplate) {

    fun insert(userId: UUID, name: String, color: SpaceColor): SpaceEntity {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO feed.spaces (id, user_id, name, color, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            id, userId, name, color.name, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)
        )
        return SpaceEntity(id, userId, name, color, now, now)
    }

    fun findByIdAndUserId(id: UUID, userId: UUID): SpaceEntity? {
        val rows = jdbcTemplate.query(
            "SELECT id, user_id, name, color, created_at, updated_at FROM feed.spaces WHERE id = ? AND user_id = ?",
            { rs: ResultSet, _ -> mapRow(rs) },
            id, userId
        )
        return rows.firstOrNull()
    }

    fun findAllByUserId(userId: UUID): List<SpaceEntity> {
        return jdbcTemplate.query(
            "SELECT id, user_id, name, color, created_at, updated_at FROM feed.spaces " +
                "WHERE user_id = ? ORDER BY created_at ASC",
            { rs: ResultSet, _ -> mapRow(rs) },
            userId
        )
    }

    fun countByUserId(userId: UUID): Int {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feed.spaces WHERE user_id = ?",
            Int::class.java,
            userId
        ) ?: 0
    }

    fun countByUserIdForUpdate(userId: UUID): Int {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feed.spaces WHERE user_id = ?",
            Int::class.java,
            userId
        ) ?: 0
    }

    fun existsByUserIdAndName(userId: UUID, name: String): Boolean {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feed.spaces WHERE user_id = ? AND name = ?",
            Int::class.java,
            userId, name
        ) ?: 0
        return count > 0
    }

    fun existsByUserIdAndNameExcludingId(userId: UUID, name: String, excludeId: UUID): Boolean {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feed.spaces WHERE user_id = ? AND name = ? AND id <> ?",
            Int::class.java,
            userId, name, excludeId
        ) ?: 0
        return count > 0
    }

    fun update(id: UUID, name: String?, color: SpaceColor?): Int {
        val sets = mutableListOf<String>()
        val params = mutableListOf<Any>()
        if (name != null) {
            sets.add("name = ?")
            params.add(name)
        }
        if (color != null) {
            sets.add("color = ?")
            params.add(color.name)
        }
        sets.add("updated_at = ?")
        params.add(java.sql.Timestamp.from(Instant.now()))
        params.add(id)
        return jdbcTemplate.update(
            "UPDATE feed.spaces SET ${sets.joinToString(", ")} WHERE id = ?",
            *params.toTypedArray()
        )
    }

    fun touchUpdatedAt(id: UUID): Int {
        return jdbcTemplate.update(
            "UPDATE feed.spaces SET updated_at = ? WHERE id = ?",
            java.sql.Timestamp.from(Instant.now()), id
        )
    }

    fun deleteByIdAndUserId(id: UUID, userId: UUID): Int {
        return jdbcTemplate.update(
            "DELETE FROM feed.spaces WHERE id = ? AND user_id = ?",
            id, userId
        )
    }

    private fun mapRow(rs: ResultSet): SpaceEntity {
        return SpaceEntity(
            id = rs.getObject("id", UUID::class.java),
            userId = rs.getObject("user_id", UUID::class.java),
            name = rs.getString("name"),
            color = SpaceColor.valueOf(rs.getString("color")),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }
}
