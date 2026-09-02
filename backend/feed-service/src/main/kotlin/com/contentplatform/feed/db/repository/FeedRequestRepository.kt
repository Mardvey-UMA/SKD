package com.contentplatform.feed.db.repository

import com.contentplatform.feed.db.entity.FeedRequestEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class FeedRequestRepository(private val jdbcTemplate: JdbcTemplate) {

    fun save(entity: FeedRequestEntity) {
        jdbcTemplate.update(
            """
            INSERT INTO feed.feed_requests
                (request_id, user_id, requested_at, page_number, source, count_requested,
                 count_returned, latency_ms, latency_breakdown, feature_flags, ab_bucket,
                 app_version, device_type)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
            """.trimIndent(),
            entity.requestId,
            entity.userId,
            java.sql.Timestamp.from(entity.requestedAt),
            entity.pageNumber,
            entity.source,
            entity.countRequested,
            entity.countReturned,
            entity.latencyMs,
            entity.latencyBreakdown,
            entity.featureFlags,
            entity.abBucket,
            entity.appVersion,
            entity.deviceType
        )
    }

    fun findByRequestId(requestId: UUID): FeedRequestEntity? {
        val results = jdbcTemplate.query(
            """
            SELECT request_id, user_id, requested_at, page_number, source, count_requested,
                   count_returned, latency_ms, latency_breakdown, feature_flags, ab_bucket,
                   app_version, device_type
            FROM feed.feed_requests
            WHERE request_id = ?
            """.trimIndent(),
            { rs: ResultSet, _ -> mapRow(rs) },
            requestId
        )
        return results.firstOrNull()
    }

    private fun mapRow(rs: ResultSet): FeedRequestEntity {
        val requestId = rs.getObject("request_id", UUID::class.java)
        val userId = rs.getObject("user_id", UUID::class.java)
        val requestedAt = rs.getTimestamp("requested_at").toInstant()
        val pageNumber = rs.getInt("page_number")
        val source = rs.getString("source")
        val countRequested = rs.getInt("count_requested")
        val countReturned = rs.getInt("count_returned")
        val latencyMsRaw = rs.getInt("latency_ms")
        val latencyMs = if (rs.wasNull()) null else latencyMsRaw
        val latencyBreakdown = rs.getString("latency_breakdown")
        val featureFlags = rs.getString("feature_flags")
        val abBucket = rs.getInt("ab_bucket")
        val appVersion = rs.getString("app_version")
        val deviceType = rs.getString("device_type")
        return FeedRequestEntity(
            requestId = requestId,
            userId = userId,
            requestedAt = requestedAt,
            pageNumber = pageNumber,
            source = source,
            countRequested = countRequested,
            countReturned = countReturned,
            latencyMs = latencyMs,
            latencyBreakdown = latencyBreakdown,
            featureFlags = featureFlags,
            abBucket = abBucket,
            appVersion = appVersion,
            deviceType = deviceType
        )
    }
}
