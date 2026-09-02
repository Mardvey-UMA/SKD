package com.contentplatform.feed.integration.db

import com.contentplatform.feed.db.repository.SourceAdditionsRepository
import com.contentplatform.feed.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

@Tag("integration")
class SourceAdditionsRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var repository: SourceAdditionsRepository
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM feed.source_additions")
    }

    @Test
    fun `upsert idempotent on conflict`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val ts = Instant.parse("2026-04-16T10:00:00Z")

        val first = repository.upsert(userId, sourceId, "rss", "Source A", ts)
        val second = repository.upsert(userId, sourceId, "rss", "Source A", ts)

        assertThat(first).isEqualTo(1)
        assertThat(second).isEqualTo(0)
        assertThat(repository.countByUserId(userId)).isEqualTo(1)
    }

    @Test
    fun `findByUserId returns rows ordered by added_at DESC`() {
        val userId = UUID.randomUUID()
        val src1 = UUID.randomUUID()
        val src2 = UUID.randomUUID()
        val src3 = UUID.randomUUID()

        repository.upsert(userId, src1, "rss", "Src 1", Instant.parse("2026-04-10T00:00:00Z"))
        repository.upsert(userId, src2, "rss", "Src 2", Instant.parse("2026-04-15T00:00:00Z"))
        repository.upsert(userId, src3, "rss", "Src 3", Instant.parse("2026-04-12T00:00:00Z"))

        val results = repository.findByUserId(userId, 10, 0)
        assertThat(results.map { it.sourceId }).containsExactly(src2, src3, src1)
    }

    @Test
    fun `countByUser returns correct total`() {
        val userId = UUID.randomUUID()
        repository.upsert(userId, UUID.randomUUID(), "rss", "a", Instant.now())
        repository.upsert(userId, UUID.randomUUID(), "rss", "b", Instant.now())
        assertThat(repository.countByUserId(userId)).isEqualTo(2)
        assertThat(repository.countByUserId(UUID.randomUUID())).isEqualTo(0)
    }

    @Test
    fun `findByUserId paginates with limit and offset`() {
        val userId = UUID.randomUUID()
        repeat(5) {
            repository.upsert(
                userId, UUID.randomUUID(), "rss", "Src $it",
                Instant.parse("2026-04-${10 + it}T00:00:00Z")
            )
        }

        val page1 = repository.findByUserId(userId, 2, 0)
        val page2 = repository.findByUserId(userId, 2, 2)
        assertThat(page1).hasSize(2)
        assertThat(page2).hasSize(2)
        assertThat(page1.first().sourceId).isNotEqualTo(page2.first().sourceId)
    }
}
