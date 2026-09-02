package com.contentplatform.feed.integration.db

import com.contentplatform.feed.db.repository.BlockedSourceRepository
import com.contentplatform.feed.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Tag("integration")
class BlockedSourceRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var repository: BlockedSourceRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM feed.blocked_sources")
        jdbcTemplate.execute("DELETE FROM feed.source_additions")
    }

    private fun seedBlocked(userId: UUID, sourceId: UUID, blockedAt: Instant) {
        jdbcTemplate.update(
            "INSERT INTO feed.blocked_sources (user_id, source_id, blocked_at) VALUES (?, ?, ?)",
            userId, sourceId, Timestamp.from(blockedAt)
        )
    }

    private fun seedSourceAddition(
        userId: UUID,
        sourceId: UUID,
        sourceType: String,
        sourceName: String,
        addedAt: Instant = Instant.now()
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO feed.source_additions (user_id, source_id, source_type, source_name, added_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            userId, sourceId, sourceType, sourceName, Timestamp.from(addedAt)
        )
    }

    @Test
    fun `findByUserIdWithMetadata returns sourceType and sourceName when source_additions row exists`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val blockedAt = Instant.parse("2026-04-21T12:00:00Z")

        seedSourceAddition(userId, sourceId, "rss", "My Favourite Feed")
        seedBlocked(userId, sourceId, blockedAt)

        val result = repository.findByUserIdWithMetadata(userId)

        assertThat(result).hasSize(1)
        val row = result.first()
        assertThat(row.userId).isEqualTo(userId)
        assertThat(row.sourceId).isEqualTo(sourceId)
        assertThat(row.blockedAt).isEqualTo(blockedAt)
        assertThat(row.sourceType).isEqualTo("rss")
        assertThat(row.sourceName).isEqualTo("My Favourite Feed")
    }

    @Test
    fun `findByUserIdWithMetadata returns null metadata when source_additions row is missing (LEFT JOIN miss)`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val blockedAt = Instant.parse("2026-04-21T10:00:00Z")

        seedBlocked(userId, sourceId, blockedAt)

        val result = repository.findByUserIdWithMetadata(userId)

        assertThat(result).hasSize(1)
        val row = result.first()
        assertThat(row.sourceId).isEqualTo(sourceId)
        assertThat(row.blockedAt).isEqualTo(blockedAt)
        assertThat(row.sourceType).isNull()
        assertThat(row.sourceName).isNull()
    }

    @Test
    fun `findByUserIdWithMetadata orders rows by blocked_at DESC`() {
        val userId = UUID.randomUUID()
        val oldest = UUID.randomUUID()
        val middle = UUID.randomUUID()
        val newest = UUID.randomUUID()

        seedBlocked(userId, oldest, Instant.parse("2026-04-19T00:00:00Z"))
        seedBlocked(userId, middle, Instant.parse("2026-04-20T00:00:00Z"))
        seedBlocked(userId, newest, Instant.parse("2026-04-21T00:00:00Z"))

        val result = repository.findByUserIdWithMetadata(userId)

        assertThat(result.map { it.sourceId }).containsExactly(newest, middle, oldest)
    }

    @Test
    fun `findByUserIdWithMetadata returns only rows for requested user`() {
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()
        val sharedSource = UUID.randomUUID()
        val onlyUserBSource = UUID.randomUUID()

        seedSourceAddition(userA, sharedSource, "rss", "User A Source")
        seedBlocked(userA, sharedSource, Instant.parse("2026-04-21T00:00:00Z"))

        seedSourceAddition(userB, sharedSource, "rss", "User B Source")
        seedBlocked(userB, sharedSource, Instant.parse("2026-04-21T00:00:00Z"))
        seedBlocked(userB, onlyUserBSource, Instant.parse("2026-04-20T00:00:00Z"))

        val resultA = repository.findByUserIdWithMetadata(userA)
        val resultB = repository.findByUserIdWithMetadata(userB)

        assertThat(resultA).hasSize(1)
        assertThat(resultA.first().userId).isEqualTo(userA)
        assertThat(resultA.first().sourceName).isEqualTo("User A Source")

        assertThat(resultB).hasSize(2)
        assertThat(resultB.map { it.userId }).allMatch { it == userB }
        assertThat(resultB.map { it.sourceId }).containsExactlyInAnyOrder(sharedSource, onlyUserBSource)
    }
}
