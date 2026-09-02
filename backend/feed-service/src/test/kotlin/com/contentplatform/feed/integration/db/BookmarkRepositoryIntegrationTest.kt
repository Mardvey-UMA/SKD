package com.contentplatform.feed.integration.db

import com.contentplatform.feed.db.repository.BookmarkRepository
import com.contentplatform.feed.db.repository.model.UserBookmarkEntity
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
import java.time.Instant
import java.util.UUID

@Tag("integration")
class BookmarkRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var bookmarkRepository: BookmarkRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM feed.user_bookmarks")
    }

    @Nested
    inner class `insert and find` {

        @Test
        fun `should insert bookmark and find by userId`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            bookmarkRepository.insert(userId, contentId)

            val bookmarks = bookmarkRepository.findByUserId(userId, 20, 0)
            assertThat(bookmarks).hasSize(1)
            assertThat(bookmarks[0].userId).isEqualTo(userId)
            assertThat(bookmarks[0].contentId).isEqualTo(contentId)
            assertThat(bookmarks[0].createdAt).isNotNull()
        }

        @Test
        fun `should return empty list when user has no bookmarks`() {
            val bookmarks = bookmarkRepository.findByUserId(UUID.randomUUID(), 20, 0)
            assertThat(bookmarks).isEmpty()
        }
    }

    @Nested
    inner class `exists check` {

        @Test
        fun `should return true when bookmark exists`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            bookmarkRepository.insert(userId, contentId)

            val exists = bookmarkRepository.existsByUserIdAndContentId(userId, contentId)
            assertThat(exists).isTrue()
        }

        @Test
        fun `should return false when bookmark does not exist`() {
            val exists = bookmarkRepository.existsByUserIdAndContentId(UUID.randomUUID(), UUID.randomUUID())
            assertThat(exists).isFalse()
        }
    }

    @Nested
    inner class `delete` {

        @Test
        fun `should delete existing bookmark`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            bookmarkRepository.insert(userId, contentId)

            bookmarkRepository.deleteByUserIdAndContentId(userId, contentId)

            val exists = bookmarkRepository.existsByUserIdAndContentId(userId, contentId)
            assertThat(exists).isFalse()
        }

        @Test
        fun `should not throw when deleting non-existent bookmark`() {
            bookmarkRepository.deleteByUserIdAndContentId(UUID.randomUUID(), UUID.randomUUID())
            // no exception expected
        }
    }

    @Nested
    inner class `ordering` {

        @Test
        fun `should return bookmarks ordered by created_at descending`() {
            val userId = UUID.randomUUID()
            val contentId1 = UUID.randomUUID()
            val contentId2 = UUID.randomUUID()
            val contentId3 = UUID.randomUUID()

            // Insert with small delays to ensure distinct timestamps
            bookmarkRepository.insert(userId, contentId1)
            Thread.sleep(50)
            bookmarkRepository.insert(userId, contentId2)
            Thread.sleep(50)
            bookmarkRepository.insert(userId, contentId3)

            val bookmarks = bookmarkRepository.findByUserId(userId, 20, 0)
            assertThat(bookmarks).hasSize(3)
            // Most recent first
            assertThat(bookmarks[0].contentId).isEqualTo(contentId3)
            assertThat(bookmarks[1].contentId).isEqualTo(contentId2)
            assertThat(bookmarks[2].contentId).isEqualTo(contentId1)
        }
    }

    @Nested
    inner class `duplicate insert` {

        @Test
        fun `should throw on duplicate insert with same userId and contentId`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            bookmarkRepository.insert(userId, contentId)

            assertThatThrownBy {
                bookmarkRepository.insert(userId, contentId)
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
                bookmarkRepository.insert(userId, contentId)
                Thread.sleep(50)
            }

            // Page 1: first 2 items (most recent)
            val page1 = bookmarkRepository.findByUserId(userId, 2, 0)
            assertThat(page1).hasSize(2)
            assertThat(page1[0].contentId).isEqualTo(contentIds[4])
            assertThat(page1[1].contentId).isEqualTo(contentIds[3])

            // Page 2: next 2 items
            val page2 = bookmarkRepository.findByUserId(userId, 2, 2)
            assertThat(page2).hasSize(2)
            assertThat(page2[0].contentId).isEqualTo(contentIds[2])
            assertThat(page2[1].contentId).isEqualTo(contentIds[1])

            // Page 3: last item
            val page3 = bookmarkRepository.findByUserId(userId, 2, 4)
            assertThat(page3).hasSize(1)
            assertThat(page3[0].contentId).isEqualTo(contentIds[0])
        }

        @Test
        fun `should return empty list when offset exceeds total count`() {
            val userId = UUID.randomUUID()
            bookmarkRepository.insert(userId, UUID.randomUUID())

            val result = bookmarkRepository.findByUserId(userId, 20, 100)
            assertThat(result).isEmpty()
        }
    }
}
