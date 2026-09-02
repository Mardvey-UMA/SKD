package com.contentplatform.feed.integration.service

import com.contentplatform.feed.application.CollectionService
import com.contentplatform.feed.application.exception.AlreadyExistsException
import com.contentplatform.feed.db.repository.BookmarkRepository
import com.contentplatform.feed.db.repository.DislikeRepository
import com.contentplatform.feed.db.repository.LikeRepository
import com.contentplatform.feed.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

@Tag("integration")
class CollectionServiceIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var collectionService: CollectionService

    @Autowired
    lateinit var bookmarkRepository: BookmarkRepository

    @Autowired
    lateinit var likeRepository: LikeRepository

    @Autowired
    lateinit var dislikeRepository: DislikeRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM feed.user_bookmarks")
        jdbcTemplate.execute("DELETE FROM feed.user_likes")
        jdbcTemplate.execute("DELETE FROM feed.user_dislikes")
    }

    @Nested
    inner class `like removes dislike` {

        @Test
        fun `adding a like should remove existing dislike from database`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            // First, dislike the content
            collectionService.addDislike(userId, contentId)
            assertThat(dislikeRepository.existsByUserIdAndContentId(userId, contentId)).isTrue()

            // Now like the same content -- dislike should be removed
            collectionService.addLike(userId, contentId)

            assertThat(likeRepository.existsByUserIdAndContentId(userId, contentId)).isTrue()
            assertThat(dislikeRepository.existsByUserIdAndContentId(userId, contentId)).isFalse()
        }

        @Test
        fun `adding a like should work even when no prior dislike exists`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            collectionService.addLike(userId, contentId)

            assertThat(likeRepository.existsByUserIdAndContentId(userId, contentId)).isTrue()
            assertThat(dislikeRepository.existsByUserIdAndContentId(userId, contentId)).isFalse()
        }
    }

    @Nested
    inner class `dislike removes like` {

        @Test
        fun `adding a dislike should remove existing like from database`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            // First, like the content
            collectionService.addLike(userId, contentId)
            assertThat(likeRepository.existsByUserIdAndContentId(userId, contentId)).isTrue()

            // Now dislike the same content -- like should be removed
            collectionService.addDislike(userId, contentId)

            assertThat(dislikeRepository.existsByUserIdAndContentId(userId, contentId)).isTrue()
            assertThat(likeRepository.existsByUserIdAndContentId(userId, contentId)).isFalse()
        }

        @Test
        fun `adding a dislike should work even when no prior like exists`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            collectionService.addDislike(userId, contentId)

            assertThat(dislikeRepository.existsByUserIdAndContentId(userId, contentId)).isTrue()
            assertThat(likeRepository.existsByUserIdAndContentId(userId, contentId)).isFalse()
        }
    }

    @Nested
    inner class `duplicate bookmark` {

        @Test
        fun `adding same bookmark twice should throw AlreadyExistsException`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            collectionService.addBookmark(userId, contentId)

            assertThatThrownBy {
                collectionService.addBookmark(userId, contentId)
            }.isInstanceOf(AlreadyExistsException::class.java)
        }

        @Test
        fun `adding same like twice should throw AlreadyExistsException`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            collectionService.addLike(userId, contentId)

            assertThatThrownBy {
                collectionService.addLike(userId, contentId)
            }.isInstanceOf(AlreadyExistsException::class.java)
        }

        @Test
        fun `adding same dislike twice should throw AlreadyExistsException`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            collectionService.addDislike(userId, contentId)

            assertThatThrownBy {
                collectionService.addDislike(userId, contentId)
            }.isInstanceOf(AlreadyExistsException::class.java)
        }
    }

    @Nested
    inner class `content status` {

        @Test
        fun `should return correct status for liked and bookmarked content`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            collectionService.addLike(userId, contentId)
            collectionService.addBookmark(userId, contentId)

            val status = collectionService.getContentStatus(userId, contentId)

            assertThat(status.liked).isTrue()
            assertThat(status.disliked).isFalse()
            assertThat(status.bookmarked).isTrue()
        }

        @Test
        fun `should return all false for content with no interactions`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            val status = collectionService.getContentStatus(userId, contentId)

            assertThat(status.liked).isFalse()
            assertThat(status.disliked).isFalse()
            assertThat(status.bookmarked).isFalse()
        }

        @Test
        fun `should reflect mutual exclusion in status after switching from like to dislike`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            collectionService.addLike(userId, contentId)
            collectionService.addDislike(userId, contentId)

            val status = collectionService.getContentStatus(userId, contentId)

            assertThat(status.liked).isFalse()
            assertThat(status.disliked).isTrue()
            assertThat(status.bookmarked).isFalse()
        }
    }
}
