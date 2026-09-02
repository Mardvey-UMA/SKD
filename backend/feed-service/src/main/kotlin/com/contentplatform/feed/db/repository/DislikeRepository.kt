package com.contentplatform.feed.db.repository

import com.contentplatform.feed.db.repository.model.UserDislikeEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class DislikeRepository(private val jdbcTemplate: JdbcTemplate) {

    fun insert(userId: UUID, contentId: UUID) {
        jdbcTemplate.update(
            "INSERT INTO feed.user_dislikes (user_id, content_id) VALUES (?, ?)",
            userId, contentId
        )
    }

    fun findByUserId(userId: UUID, limit: Int, offset: Int): List<UserDislikeEntity> {
        return jdbcTemplate.query(
            "SELECT user_id, content_id, created_at FROM feed.user_dislikes " +
                "WHERE user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            { rs: ResultSet, _ -> mapRow(rs) },
            userId, limit, offset
        )
    }

    fun existsByUserIdAndContentId(userId: UUID, contentId: UUID): Boolean {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feed.user_dislikes WHERE user_id = ? AND content_id = ?",
            Int::class.java,
            userId, contentId
        ) ?: 0
        return count > 0
    }

    fun deleteByUserIdAndContentId(userId: UUID, contentId: UUID) {
        jdbcTemplate.update(
            "DELETE FROM feed.user_dislikes WHERE user_id = ? AND content_id = ?",
            userId, contentId
        )
    }

    fun findDislikedContentIds(userId: UUID, contentIds: Collection<UUID>): Set<UUID> {
        if (contentIds.isEmpty()) return emptySet()
        val placeholders = contentIds.joinToString(",") { "?" }
        val params = arrayOf<Any>(userId, *contentIds.toTypedArray())
        return jdbcTemplate.queryForList(
            "SELECT content_id FROM feed.user_dislikes WHERE user_id = ? AND content_id IN ($placeholders)",
            UUID::class.java,
            *params
        ).toSet()
    }

    private fun mapRow(rs: ResultSet): UserDislikeEntity {
        return UserDislikeEntity(
            userId = rs.getObject("user_id", UUID::class.java),
            contentId = rs.getObject("content_id", UUID::class.java),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }
}
