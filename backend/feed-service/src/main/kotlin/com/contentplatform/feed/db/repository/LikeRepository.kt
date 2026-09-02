package com.contentplatform.feed.db.repository

import com.contentplatform.feed.db.repository.model.UserLikeEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class LikeRepository(private val jdbcTemplate: JdbcTemplate) {

    fun insert(userId: UUID, contentId: UUID) {
        jdbcTemplate.update(
            "INSERT INTO feed.user_likes (user_id, content_id) VALUES (?, ?)",
            userId, contentId
        )
    }

    fun findByUserId(userId: UUID, limit: Int, offset: Int): List<UserLikeEntity> {
        return jdbcTemplate.query(
            "SELECT user_id, content_id, created_at FROM feed.user_likes " +
                "WHERE user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            { rs: ResultSet, _ -> mapRow(rs) },
            userId, limit, offset
        )
    }

    fun existsByUserIdAndContentId(userId: UUID, contentId: UUID): Boolean {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feed.user_likes WHERE user_id = ? AND content_id = ?",
            Int::class.java,
            userId, contentId
        ) ?: 0
        return count > 0
    }

    fun deleteByUserIdAndContentId(userId: UUID, contentId: UUID) {
        jdbcTemplate.update(
            "DELETE FROM feed.user_likes WHERE user_id = ? AND content_id = ?",
            userId, contentId
        )
    }

    private fun mapRow(rs: ResultSet): UserLikeEntity {
        return UserLikeEntity(
            userId = rs.getObject("user_id", UUID::class.java),
            contentId = rs.getObject("content_id", UUID::class.java),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }
}
