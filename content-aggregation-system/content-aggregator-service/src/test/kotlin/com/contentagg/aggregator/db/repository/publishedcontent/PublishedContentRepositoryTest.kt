package com.contentagg.aggregator.db.repository.publishedcontent

import com.contentagg.aggregator.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.jdbc.Sql
import java.util.UUID

@Sql(
    scripts = ["classpath:db/test-data.sql"],
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class PublishedContentRepositoryTest : IntegrationTestBase() {

    @Autowired
    private lateinit var repository: PublishedContentRepository

    @Nested
    @DisplayName("findBySourceType")
    inner class FindBySourceTypeTests {

        @Test
        @DisplayName("returns HABR content")
        fun habrContent_returned() {
            val page = repository.findBySourceType("HABR", PageRequest.of(0, 10))
            assertEquals(4, page.totalElements)
        }

        @Test
        @DisplayName("returns empty page for unknown source type")
        fun unknownType_returnsEmpty() {
            val page = repository.findBySourceType("UNKNOWN", PageRequest.of(0, 10))
            assertEquals(0, page.totalElements)
        }

        @Test
        @DisplayName("pagination works correctly")
        fun pagination_returnsCorrectPage() {
            val page = repository.findBySourceType("HABR", PageRequest.of(0, 2))
            assertEquals(4, page.totalElements)
            assertEquals(2, page.content.size)
            assertEquals(2, page.totalPages)
        }
    }

    @Nested
    @DisplayName("countBySourceType")
    inner class CountBySourceTypeTests {

        @Test
        @DisplayName("counts HABR records")
        fun countsHabr() {
            assertEquals(4, repository.countBySourceType("HABR"))
        }

        @Test
        @DisplayName("counts TELEGRAM records")
        fun countsTelegram() {
            assertEquals(1, repository.countBySourceType("TELEGRAM"))
        }

        @Test
        @DisplayName("returns 0 for unknown type")
        fun unknownType_returnsZero() {
            assertEquals(0, repository.countBySourceType("UNKNOWN"))
        }
    }

    @Nested
    @DisplayName("searchByText")
    inner class SearchByTextTests {

        @Test
        @DisplayName("finds by title ILIKE")
        fun findsByTitle() {
            val results = repository.searchByText("kotlin", 10, 0)
            assertEquals(2, results.size)
        }

        @Test
        @DisplayName("finds by description ILIKE")
        fun findsByDescription() {
            val results = repository.searchByText("optimization", 10, 0)
            assertEquals(1, results.size)
            assertEquals("PostgreSQL performance tips", results[0].title)
        }

        @Test
        @DisplayName("case-insensitive search")
        fun caseInsensitive() {
            val results = repository.searchByText("KOTLIN", 10, 0)
            assertEquals(2, results.size)
        }

        @Test
        @DisplayName("returns empty for no match")
        fun noMatch_returnsEmpty() {
            val results = repository.searchByText("nonexistent_query_xyz", 10, 0)
            assertEquals(0, results.size)
        }
    }

    @Nested
    @DisplayName("countByText")
    inner class CountByTextTests {

        @Test
        @DisplayName("counts search results")
        fun countsResults() {
            assertEquals(2, repository.countByText("kotlin"))
        }

        @Test
        @DisplayName("returns 0 for no match")
        fun noMatch_returnsZero() {
            assertEquals(0, repository.countByText("nonexistent_xyz"))
        }
    }

    @Nested
    @DisplayName("findAllOrderByPublishedAtDescNullsLast")
    inner class FindAllOrderedTests {

        @Test
        @DisplayName("returns records ordered by publishedAt DESC NULLS LAST")
        fun allOrderedDesc() {
            val results = repository.findAllOrderByPublishedAtDescNullsLast(100, 0)
            assertEquals(7, results.size)
            val dates = results.mapNotNull { it.publishedAt }
            assertEquals(dates.sortedDescending(), dates)
        }

        @Test
        @DisplayName("pagination works correctly")
        fun pagination() {
            val page1 = repository.findAllOrderByPublishedAtDescNullsLast(3, 0)
            assertEquals(3, page1.size)
            val page2 = repository.findAllOrderByPublishedAtDescNullsLast(3, 3)
            assertEquals(3, page2.size)
        }
    }

    @Nested
    @DisplayName("findAllByIdIn")
    inner class FindAllByIdInTests {

        @Test
        @DisplayName("returns entities for given IDs")
        fun returnsForIds() {
            val ids = arrayOf(
                UUID.fromString("a0000000-0000-0000-0000-000000000001"),
                UUID.fromString("a0000000-0000-0000-0000-000000000002"),
            )
            val results = repository.findAllByIdIn(ids)
            assertEquals(2, results.size)
        }

        @Test
        @DisplayName("returns empty for empty array")
        fun emptyIds_returnsEmpty() {
            val results = repository.findAllByIdIn(emptyArray())
            assertEquals(0, results.size)
        }
    }

    @Nested
    @DisplayName("findById (CrudRepository)")
    inner class FindByIdTests {

        @Test
        @DisplayName("returns entity for existing ID")
        fun existingId_returnsEntity() {
            val result = repository.findById(
                UUID.fromString("a0000000-0000-0000-0000-000000000001")
            )
            assertTrue(result.isPresent)
            assertEquals("habr-001", result.get().externalId)
        }

        @Test
        @DisplayName("returns empty for missing ID")
        fun missingId_returnsEmpty() {
            val result = repository.findById(UUID.randomUUID())
            assertFalse(result.isPresent)
        }
    }
}
