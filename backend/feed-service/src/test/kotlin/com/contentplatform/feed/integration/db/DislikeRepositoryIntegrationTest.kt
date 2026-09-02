package com.contentplatform.feed.integration.db

import com.contentplatform.feed.db.repository.DislikeRepository
import com.contentplatform.feed.db.repository.model.UserDislikeEntity
import com.contentplatform.feed.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

@Tag("integration")
class DislikeRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var dislikeRepository: DislikeRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM feed.user_dislikes")
    }

    @Nested
    inner class `insert and find` {

        @Test
        fun `should insert dislike and find by userId`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            dislikeRepository.insert(userId, contentId)

            val dislikes = dislikeRepository.findByUserId(userId, 20, 0)
            assertThat(dislikes).hasSize(1)
            assertThat(dislikes[0].userId).isEqualTo(userId)
            assertThat(dislikes[0].contentId).isEqualTo(contentId)
            assertThat(dislikes[0].createdAt).isNotNull()
        }

        @Test
        fun `should return empty list when user has no dislikes`() {
            val dislikes = dislikeRepository.findByUserId(UUID.randomUUID(), 20, 0)
            assertThat(dislikes).isEmpty()
        }
    }

    @Nested
    inner class `exists check` {

        @Test
        fun `should return true when dislike exists`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            dislikeRepository.insert(userId, contentId)

            val exists = dislikeRepository.existsByUserIdAndContentId(userId, contentId)
            assertThat(exists).isTrue()
        }

        @Test
        fun `should return false when dislike does not exist`() {
            val exists = dislikeRepository.existsByUserIdAndContentId(UUID.randomUUID(), UUID.randomUUID())
            assertThat(exists).isFalse()
        }
    }

    @Nested
    inner class `delete` {

        @Test
        fun `should delete existing dislike`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            dislikeRepository.insert(userId, contentId)

            dislikeRepository.deleteByUserIdAndContentId(userId, contentId)

            val exists = dislikeRepository.existsByUserIdAndContentId(userId, contentId)
            assertThat(exists).isFalse()
        }

        @Test
        fun `should not throw when deleting non-existent dislike`() {
            dislikeRepository.deleteByUserIdAndContentId(UUID.randomUUID(), UUID.randomUUID())
        }
    }

    @Nested
    inner class `ordering` {

        @Test
        fun `should return dislikes ordered by created_at descending`() {
            val userId = UUID.randomUUID()
            val contentId1 = UUID.randomUUID()
            val contentId2 = UUID.randomUUID()
            val contentId3 = UUID.randomUUID()

            dislikeRepository.insert(userId, contentId1)
            Thread.sleep(50)
            dislikeRepository.insert(userId, contentId2)
            Thread.sleep(50)
            dislikeRepository.insert(userId, contentId3)

            val dislikes = dislikeRepository.findByUserId(userId, 20, 0)
            assertThat(dislikes).hasSize(3)
            assertThat(dislikes[0].contentId).isEqualTo(contentId3)
            assertThat(dislikes[1].contentId).isEqualTo(contentId2)
            assertThat(dislikes[2].contentId).isEqualTo(contentId1)
        }
    }

    @Nested
    inner class `duplicate insert` {

        @Test
        fun `should throw on duplicate insert with same userId and contentId`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            dislikeRepository.insert(userId, contentId)

            assertThatThrownBy {
                dislikeRepository.insert(userId, contentId)
            }.isInstanceOf(DuplicateKeyException::class.java)
        }
    }

    @Nested
    inner class `pagination` {

        @Test
        fun `should return correct page with limit and offset`() {
            val userId = UUID.randomUUID()
            val contentIds = (1..5).map { UUID.randomUUID() }

            contentIds.forEach { contentId ->
                dislikeRepository.insert(userId, contentId)
                Thread.sleep(50)
            }

            val page1 = dislikeRepository.findByUserId(userId, 2, 0)
            assertThat(page1).hasSize(2)
            assertThat(page1[0].contentId).isEqualTo(contentIds[4])
            assertThat(page1[1].contentId).isEqualTo(contentIds[3])

            val page2 = dislikeRepository.findByUserId(userId, 2, 2)
            assertThat(page2).hasSize(2)
            assertThat(page2[0].contentId).isEqualTo(contentIds[2])
            assertThat(page2[1].contentId).isEqualTo(contentIds[1])
        }

        @Test
        fun `should return empty list when offset exceeds total count`() {
            val userId = UUID.randomUUID()
            dislikeRepository.insert(userId, UUID.randomUUID())

            val result = dislikeRepository.findByUserId(userId, 20, 100)
            assertThat(result).isEmpty()
        }
    }
}
