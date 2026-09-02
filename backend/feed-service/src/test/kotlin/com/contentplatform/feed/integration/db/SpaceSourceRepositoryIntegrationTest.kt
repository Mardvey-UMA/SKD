package com.contentplatform.feed.integration.db

import com.contentplatform.feed.db.repository.SpaceRepository
import com.contentplatform.feed.db.repository.SpaceSourceRepository
import com.contentplatform.feed.domain.SpaceColor
import com.contentplatform.feed.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

@Tag("integration")
class SpaceSourceRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var spaceRepo: SpaceRepository
    @Autowired lateinit var spaceSourceRepo: SpaceSourceRepository
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM feed.space_sources")
        jdbcTemplate.execute("DELETE FROM feed.spaces")
    }

    @Test
    fun `batch insert and find ids`() {
        val userId = UUID.randomUUID()
        val space = spaceRepo.insert(userId, "X", SpaceColor.RED)
        val srcA = UUID.randomUUID()
        val srcB = UUID.randomUUID()

        spaceSourceRepo.insertAll(space.id, listOf(srcA, srcB))

        val ids = spaceSourceRepo.findSourceIds(space.id)
        assertThat(ids).containsExactlyInAnyOrder(srcA, srcB)
    }

    @Test
    fun `cascade delete when parent space removed`() {
        val userId = UUID.randomUUID()
        val space = spaceRepo.insert(userId, "X", SpaceColor.RED)
        spaceSourceRepo.insertAll(space.id, listOf(UUID.randomUUID(), UUID.randomUUID()))

        spaceRepo.deleteByIdAndUserId(space.id, userId)

        assertThat(spaceSourceRepo.findSourceIds(space.id)).isEmpty()
    }

    @Test
    fun `replaceAll replaces sources`() {
        val userId = UUID.randomUUID()
        val space = spaceRepo.insert(userId, "X", SpaceColor.RED)
        val src1 = UUID.randomUUID()
        val src2 = UUID.randomUUID()
        val src3 = UUID.randomUUID()

        spaceSourceRepo.insertAll(space.id, listOf(src1, src2))
        spaceSourceRepo.replaceAll(space.id, listOf(src3))

        val ids = spaceSourceRepo.findSourceIds(space.id)
        assertThat(ids).containsExactly(src3)
    }

    @Test
    fun `insertAll with empty list is noop`() {
        val userId = UUID.randomUUID()
        val space = spaceRepo.insert(userId, "X", SpaceColor.RED)
        spaceSourceRepo.insertAll(space.id, emptyList())
        assertThat(spaceSourceRepo.findSourceIds(space.id)).isEmpty()
    }

    @Test
    fun `insertAll dedupes duplicates via ON CONFLICT`() {
        val userId = UUID.randomUUID()
        val space = spaceRepo.insert(userId, "X", SpaceColor.RED)
        val src = UUID.randomUUID()
        spaceSourceRepo.insertAll(space.id, listOf(src))
        spaceSourceRepo.insertAll(space.id, listOf(src))
        assertThat(spaceSourceRepo.findSourceIds(space.id)).containsExactly(src)
    }
}
