package com.contentplatform.feed.db.repository

import com.contentplatform.feed.db.entity.FeedItemEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class FeedItemRepository(private val jdbcTemplate: JdbcTemplate) {

    fun saveAll(entities: List<FeedItemEntity>) {
        if (entities.isEmpty()) return
        entities.forEach { entity ->
            jdbcTemplate.update(
                """
                INSERT INTO feed.feed_items
                    (request_id, position, content_id, raw_content_id, final_score,
                     scoring_components, rerank_score, filtered_out_by)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """.trimIndent(),
                entity.requestId,
                entity.position,
                entity.contentId,
                entity.rawContentId,
                entity.finalScore,
                entity.scoringComponents,
                entity.rerankScore,
                entity.filteredOutBy
            )
        }
    }

    fun findByRequestId(requestId: UUID): List<FeedItemEntity> {
        return jdbcTemplate.query(
            """
            SELECT request_id, position, content_id, raw_content_id, final_score,
                   scoring_components, rerank_score, filtered_out_by
            FROM feed.feed_items
            WHERE request_id = ?
            ORDER BY position ASC
            """.trimIndent(),
            { rs: ResultSet, _ -> mapRow(rs) },
            requestId
        )
    }

    private fun mapRow(rs: ResultSet): FeedItemEntity {
        val requestId = rs.getObject("request_id", UUID::class.java)
        val position = rs.getShort("position")
        val contentId = rs.getObject("content_id", UUID::class.java)
        val rawContentId = rs.getObject("raw_content_id", UUID::class.java)
        val finalScoreRaw = rs.getFloat("final_score")
        val finalScore = if (rs.wasNull()) null else finalScoreRaw
        val scoringComponents = rs.getString("scoring_components")
        val rerankScoreRaw = rs.getFloat("rerank_score")
        val rerankScore = if (rs.wasNull()) null else rerankScoreRaw
        val filteredOutBy = rs.getString("filtered_out_by")
        return FeedItemEntity(
            requestId = requestId,
            position = position,
            contentId = contentId,
            rawContentId = rawContentId,
            finalScore = finalScore,
            scoringComponents = scoringComponents,
            rerankScore = rerankScore,
            filteredOutBy = filteredOutBy
        )
    }
}
