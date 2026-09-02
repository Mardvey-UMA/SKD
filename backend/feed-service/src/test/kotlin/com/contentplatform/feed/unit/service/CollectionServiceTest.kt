package com.contentplatform.feed.unit.service

import com.contentplatform.feed.application.CollectionService
import com.contentplatform.feed.application.dto.ContentStatusResponse
import com.contentplatform.feed.application.exception.AlreadyExistsException
import com.contentplatform.feed.db.repository.BookmarkRepository
import com.contentplatform.feed.db.repository.DislikeRepository
import com.contentplatform.feed.db.repository.LikeRepository
import com.contentplatform.feed.infrastructure.cache.FeedCacheService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException
import java.util.UUID

@Tag("unit")
class CollectionServiceTest {

    private val bookmarkRepository = mockk<BookmarkRepository>()
    private val likeRepository = mockk<LikeRepository>()
    private val dislikeRepository = mockk<DislikeRepository>()
    private val feedCacheService = mockk<FeedCacheService>()

    private val collectionService = CollectionService(
        bookmarkRepository = bookmarkRepository,
        likeRepository = likeRepository,
        dislikeRepository = dislikeRepository,
        feedCacheService = feedCacheService
    )

    @Nested
    inner class `addBookmark` {

        @Test
        fun `should insert bookmark via repository`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { bookmarkRepository.insert(userId, contentId) } just runs

            collectionService.addBookmark(userId, contentId)

            verify(exactly = 1) { bookmarkRepository.insert(userId, contentId) }
        }

        @Test
        fun `should throw AlreadyExistsException when bookmark already exists`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { bookmarkRepository.insert(userId, contentId) } throws DuplicateKeyException("duplicate")

            assertThatThrownBy {
                collectionService.addBookmark(userId, contentId)
            }.isInstanceOf(AlreadyExistsException::class.java)
        }
    }

    @Nested
    inner class `removeBookmark` {

        @Test
        fun `should delete bookmark via repository`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { bookmarkRepository.deleteByUserIdAndContentId(userId, contentId) } just runs

            collectionService.removeBookmark(userId, contentId)

            verify(exactly = 1) { bookmarkRepository.deleteByUserIdAndContentId(userId, contentId) }
        }
    }

    @Nested
    inner class `addLike mutual exclusion` {

        @Test
        fun `should insert like and remove existing dislike`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { likeRepository.insert(userId, contentId) } just runs
            every { dislikeRepository.deleteByUserIdAndContentId(userId, contentId) } just runs

            collectionService.addLike(userId, contentId)

            verify(exactly = 1) { likeRepository.insert(userId, contentId) }
            verify(exactly = 1) { dislikeRepository.deleteByUserIdAndContentId(userId, contentId) }
        }

        @Test
        fun `should throw AlreadyExistsException when like already exists`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { likeRepository.insert(userId, contentId) } throws DuplicateKeyException("duplicate")

            assertThatThrownBy {
                collectionService.addLike(userId, contentId)
            }.isInstanceOf(AlreadyExistsException::class.java)
        }
    }

    @Nested
    inner class `addDislike mutual exclusion` {

        @Test
        fun `should insert dislike and remove existing like`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { dislikeRepository.insert(userId, contentId) } just runs
            every { likeRepository.deleteByUserIdAndContentId(userId, contentId) } just runs
            every { feedCacheService.deleteFeed(userId) } just runs

            collectionService.addDislike(userId, contentId)

            verify(exactly = 1) { dislikeRepository.insert(userId, contentId) }
            verify(exactly = 1) { likeRepository.deleteByUserIdAndContentId(userId, contentId) }
            verify(exactly = 1) { feedCacheService.deleteFeed(userId) }
        }

        @Test
        fun `should throw AlreadyExistsException when dislike already exists`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { dislikeRepository.insert(userId, contentId) } throws DuplicateKeyException("duplicate")

            assertThatThrownBy {
                collectionService.addDislike(userId, contentId)
            }.isInstanceOf(AlreadyExistsException::class.java)
        }

        @Test
        fun `addDislike invalidates feed cache`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { dislikeRepository.insert(userId, contentId) } just runs
            every { likeRepository.deleteByUserIdAndContentId(userId, contentId) } just runs
            every { feedCacheService.deleteFeed(userId) } just runs

            collectionService.addDislike(userId, contentId)

            verify(exactly = 1) { feedCacheService.deleteFeed(userId) }
        }
    }

    @Nested
    inner class `getContentStatus` {

        @Test
        fun `should return all true when content is liked, disliked, and bookmarked`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { likeRepository.existsByUserIdAndContentId(userId, contentId) } returns true
            every { dislikeRepository.existsByUserIdAndContentId(userId, contentId) } returns true
            every { bookmarkRepository.existsByUserIdAndContentId(userId, contentId) } returns true

            val status = collectionService.getContentStatus(userId, contentId)

            assertThat(status.liked).isTrue()
            assertThat(status.disliked).isTrue()
            assertThat(status.bookmarked).isTrue()
        }

        @Test
        fun `should return all false when content has no interactions`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { likeRepository.existsByUserIdAndContentId(userId, contentId) } returns false
            every { dislikeRepository.existsByUserIdAndContentId(userId, contentId) } returns false
            every { bookmarkRepository.existsByUserIdAndContentId(userId, contentId) } returns false

            val status = collectionService.getContentStatus(userId, contentId)

            assertThat(status.liked).isFalse()
            assertThat(status.disliked).isFalse()
            assertThat(status.bookmarked).isFalse()
        }

        @Test
        fun `should return mixed status correctly`() {
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { likeRepository.existsByUserIdAndContentId(userId, contentId) } returns true
            every { dislikeRepository.existsByUserIdAndContentId(userId, contentId) } returns false
            every { bookmarkRepository.existsByUserIdAndContentId(userId, contentId) } returns true

            val status = collectionService.getContentStatus(userId, contentId)

            assertThat(status.liked).isTrue()
            assertThat(status.disliked).isFalse()
            assertThat(status.bookmarked).isTrue()
        }
    }
}
