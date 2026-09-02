package com.contentplatform.feed.integration.db

import com.contentplatform.feed.db.repository.SpaceRepository
import com.contentplatform.feed.domain.SpaceColor
import com.contentplatform.feed.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

@Tag("integration")
class SpaceRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var repository: SpaceRepository
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM feed.space_sources")
        jdbcTemplate.execute("DELETE FROM feed.spaces")
    }

    @Test
    fun `insert and find round trip`() {
        val userId = UUID.randomUUID()
        val entity = repository.insert(userId, "My Kotlin", SpaceColor.BLUE)

        val loaded = repository.findByIdAndUserId(entity.id, userId)
        assertThat(loaded).isNotNull
        assertThat(loaded!!.name).isEqualTo("My Kotlin")
        assertThat(loaded.color).isEqualTo(SpaceColor.BLUE)
    }

    @Test
    fun `unique user_id name constraint rejects duplicates`() {
        val userId = UUID.randomUUID()
        repository.insert(userId, "Dup", SpaceColor.RED)
        assertThatThrownBy {
            repository.insert(userId, "Dup", SpaceColor.BLUE)
        }.isInstanceOf(DuplicateKeyException::class.java)
    }

    @Test
    fun `countByUserId counts spaces`() {
        val userId = UUID.randomUUID()
        repository.insert(userId, "A", SpaceColor.RED)
        repository.insert(userId, "B", SpaceColor.BLUE)
        assertThat(repository.countByUserId(userId)).isEqualTo(2)
    }

    @Test
    fun `existsByUserIdAndName detects conflicts`() {
        val userId = UUID.randomUUID()
        repository.insert(userId, "Dup", SpaceColor.RED)
        assertThat(repository.existsByUserIdAndName(userId, "Dup")).isTrue()
        assertThat(repository.existsByUserIdAndName(userId, "Other")).isFalse()
    }

    @Test
    fun `update changes name and color`() {
        val userId = UUID.randomUUID()
        val entity = repository.insert(userId, "Old", SpaceColor.RED)

        repository.update(entity.id, "New", SpaceColor.GREEN)

        val loaded = repository.findByIdAndUserId(entity.id, userId)!!
        assertThat(loaded.name).isEqualTo("New")
        assertThat(loaded.color).isEqualTo(SpaceColor.GREEN)
    }

    @Test
    fun `delete removes space`() {
        val userId = UUID.randomUUID()
        val entity = repository.insert(userId, "X", SpaceColor.PINK)
        val affected = repository.deleteByIdAndUserId(entity.id, userId)
        assertThat(affected).isEqualTo(1)
        assertThat(repository.findByIdAndUserId(entity.id, userId)).isNull()
    }

    @Test
    fun `findByIdAndUserId returns null for wrong owner`() {
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()
        val entity = repository.insert(userA, "X", SpaceColor.PINK)
        assertThat(repository.findByIdAndUserId(entity.id, userB)).isNull()
    }
}
