package com.contentplatform.feed.integration.db

import com.contentplatform.feed.db.repository.LikeRepository
import com.contentplatform.feed.db.repository.model.UserLikeEntity
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
class LikeRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var likeRepository: LikeRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM feed.user_likes")
    }

    @Nested
    inner class `insert and find` {

        @Test
        fun `should insert like and find by userId`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            likeRepository.insert(userId, contentId)

            val likes = likeRepository.findByUserId(userId, 20, 0)
            assertThat(likes).hasSize(1)
            assertThat(likes[0].userId).isEqualTo(userId)
            assertThat(likes[0].contentId).isEqualTo(contentId)
            assertThat(likes[0].createdAt).isNotNull()
        }

        @Test
        fun `should return empty list when user has no likes`() {
            val likes = likeRepository.findByUserId(UUID.randomUUID(), 20, 0)
            assertThat(likes).isEmpty()
        }
    }

    @Nested
    inner class `exists check` {

        @Test
        fun `should return true when like exists`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            likeRepository.insert(userId, contentId)

            val exists = likeRepository.existsByUserIdAndContentId(userId, contentId)
            assertThat(exists).isTrue()
        }

        @Test
        fun `should return false when like does not exist`() {
            val exists = likeRepository.existsByUserIdAndContentId(UUID.randomUUID(), UUID.randomUUID())
            assertThat(exists).isFalse()
        }
    }

    @Nested
    inner class `delete` {

        @Test
        fun `should delete existing like`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            likeRepository.insert(userId, contentId)

            likeRepository.deleteByUserIdAndContentId(userId, contentId)

            val exists = likeRepository.existsByUserIdAndContentId(userId, contentId)
            assertThat(exists).isFalse()
        }

        @Test
        fun `should not throw when deleting non-existent like`() {
            likeRepository.deleteByUserIdAndContentId(UUID.randomUUID(), UUID.randomUUID())
        }
    }

    @Nested
    inner class `ordering` {

        @Test
        fun `should return likes ordered by created_at descending`() {
            val userId = UUID.randomUUID()
            val contentId1 = UUID.randomUUID()
            val contentId2 = UUID.randomUUID()
            val contentId3 = UUID.randomUUID()

            likeRepository.insert(userId, contentId1)
            Thread.sleep(50)
            likeRepository.insert(userId, contentId2)
            Thread.sleep(50)
            likeRepository.insert(userId, contentId3)

            val likes = likeRepository.findByUserId(userId, 20, 0)
            assertThat(likes).hasSize(3)
            assertThat(likes[0].contentId).isEqualTo(contentId3)
            assertThat(likes[1].contentId).isEqualTo(contentId2)
            assertThat(likes[2].contentId).isEqualTo(contentId1)
        }
    }

    @Nested
    inner class `duplicate insert` {

        @Test
        fun `should throw on duplicate insert with same userId and contentId`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            likeRepository.insert(userId, contentId)

            assertThatThrownBy {
                likeRepository.insert(userId, contentId)
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
                likeRepository.insert(userId, contentId)
                Thread.sleep(50)
            }

            val page1 = likeRepository.findByUserId(userId, 2, 0)
            assertThat(page1).hasSize(2)
            assertThat(page1[0].contentId).isEqualTo(contentIds[4])
            assertThat(page1[1].contentId).isEqualTo(contentIds[3])

            val page2 = likeRepository.findByUserId(userId, 2, 2)
            assertThat(page2).hasSize(2)
            assertThat(page2[0].contentId).isEqualTo(contentIds[2])
            assertThat(page2[1].contentId).isEqualTo(contentIds[1])
        }

        @Test
        fun `should return empty list when offset exceeds total count`() {
            val userId = UUID.randomUUID()
            likeRepository.insert(userId, UUID.randomUUID())

            val result = likeRepository.findByUserId(userId, 20, 100)
            assertThat(result).isEmpty()
        }
    }
}
