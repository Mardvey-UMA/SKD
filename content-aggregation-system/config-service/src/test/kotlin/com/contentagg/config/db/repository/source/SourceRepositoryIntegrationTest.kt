package com.contentagg.config.db.repository.source

import com.contentagg.config.IntegrationTestBase
import com.contentagg.config.db.repository.source.model.Source
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql
import java.util.UUID

@Sql("/db/test-data.sql")
class SourceRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var repository: SourceRepository

    @Nested
    inner class CrudOperations {

        @Test
        fun `save creates new source with generated UUID`() {
            val source = Source.newSource(
                sourceType = "HABR",
                name = "New Test Source",
                url = "https://habr.com/hub/test",
                updateFrequencyMinutes = 30,
                isActive = true,
                parameters = """{"hubId": "test"}"""
            )

            val saved = repository.save(source)

            assertThat(saved.id).isNotNull()
            assertThat(saved.name).isEqualTo("New Test Source")
            assertThat(saved.sourceType).isEqualTo("HABR")
            assertThat(saved.url).isEqualTo("https://habr.com/hub/test")
            assertThat(saved.updateFrequencyMinutes).isEqualTo(30)
            assertThat(saved.isActive).isTrue()
            assertThat(saved.parameters).isEqualTo("""{"hubId": "test"}""")
            assertThat(saved.createdAt).isNotNull()
            assertThat(saved.updatedAt).isNotNull()
        }

        @Test
        fun `findById returns existing source`() {
            val id = UUID.fromString("a0000000-0000-0000-0000-000000000001")

            val result = repository.findById(id)

            assertThat(result).isPresent
            assertThat(result.get().name).isEqualTo("Habr Hub Kotlin")
            assertThat(result.get().sourceType).isEqualTo("HABR")
        }

        @Test
        fun `findById returns empty for non-existent id`() {
            val result = repository.findById(UUID.randomUUID())

            assertThat(result).isEmpty()
        }

        @Test
        fun `findAll returns all sources`() {
            val results = repository.findAll().toList()

            assertThat(results).hasSize(7)
        }

        @Test
        fun `update modifies existing source`() {
            val id = UUID.fromString("a0000000-0000-0000-0000-000000000001")
            val existing = repository.findById(id).orElseThrow()
            val updated = existing.copy(name = "Updated Name", updateFrequencyMinutes = 90)

            val saved = repository.save(updated)

            assertThat(saved.name).isEqualTo("Updated Name")
            assertThat(saved.updateFrequencyMinutes).isEqualTo(90)
        }

        @Test
        fun `deleteById removes source`() {
            val id = UUID.fromString("a0000000-0000-0000-0000-000000000006")

            repository.deleteById(id)

            assertThat(repository.findById(id)).isEmpty()
        }

        @Test
        fun `count returns total number of sources`() {
            assertThat(repository.count()).isEqualTo(7)
        }
    }

    @Nested
    inner class DerivedQueries {

        @Test
        fun `findByIsActiveTrue returns only active sources`() {
            val results = repository.findByIsActiveTrue()

            assertThat(results).allSatisfy { source ->
                assertThat(source.isActive).isTrue()
            }
            assertThat(results).hasSize(6) // 7 total, 1 inactive
        }

        @Test
        fun `findBySourceType returns sources of given type`() {
            val results = repository.findBySourceType("HABR")

            assertThat(results).hasSize(4)
            assertThat(results).allSatisfy { source ->
                assertThat(source.sourceType).isEqualTo("HABR")
            }
        }

        @Test
        fun `findBySourceType returns empty list for non-existent type`() {
            val results = repository.findBySourceType("NONEXISTENT")

            assertThat(results).isEmpty()
        }
    }

    @Nested
    inner class CustomQueries {

        @Test
        fun `findActiveBySourceType returns only active sources of given type`() {
            val results = repository.findActiveBySourceType("HABR")

            assertThat(results).hasSize(3) // 4 HABR total, 1 inactive
            assertThat(results).allSatisfy { source ->
                assertThat(source.sourceType).isEqualTo("HABR")
                assertThat(source.isActive).isTrue()
            }
        }

        @Test
        fun `findByTypeAndName returns source when exists`() {
            val result = repository.findByTypeAndName("HABR", "Habr Hub Kotlin")

            assertThat(result).isNotNull
            assertThat(result!!.sourceType).isEqualTo("HABR")
            assertThat(result.name).isEqualTo("Habr Hub Kotlin")
        }

        @Test
        fun `findByTypeAndName returns null when not found`() {
            val result = repository.findByTypeAndName("HABR", "Nonexistent")

            assertThat(result).isNull()
        }

        @Test
        fun `existsByTypeAndName returns true for existing combination`() {
            assertThat(repository.existsByTypeAndName("HABR", "Habr Hub Kotlin")).isTrue()
        }

        @Test
        fun `existsByTypeAndName returns false for non-existing combination`() {
            assertThat(repository.existsByTypeAndName("HABR", "Nonexistent")).isFalse()
        }

        @Test
        fun `searchByName returns sources matching partial name case-insensitively`() {
            val results = repository.searchByName("habr")

            assertThat(results).isNotEmpty
            assertThat(results).allSatisfy { source ->
                assertThat(source.name.lowercase()).contains("habr")
            }
        }

        @Test
        fun `searchByName returns empty for no match`() {
            val results = repository.searchByName("zzz_nonexistent_zzz")

            assertThat(results).isEmpty()
        }

        @Test
        fun `existsByUrlExcludingId returns true when url exists for different id`() {
            val otherId = UUID.fromString("a0000000-0000-0000-0000-000000000002")

            val result = repository.existsByUrlExcludingId("https://habr.com/hub/kotlin", otherId)

            assertThat(result).isTrue()
        }

        @Test
        fun `existsByUrlExcludingId returns false when url belongs to same id`() {
            val sameId = UUID.fromString("a0000000-0000-0000-0000-000000000001")

            val result = repository.existsByUrlExcludingId("https://habr.com/hub/kotlin", sameId)

            assertThat(result).isFalse()
        }

        @Test
        fun `existsByUrlExcludingId returns false when url does not exist`() {
            val anyId = UUID.fromString("a0000000-0000-0000-0000-000000000001")

            val result = repository.existsByUrlExcludingId("https://nonexistent.com", anyId)

            assertThat(result).isFalse()
        }

        @Test
        fun `findSourcesNeedingUpdate returns active sources with stale updated_at`() {
            // All test data has updated_at in the past (Jan 2026), so all active sources should be "needing update"
            val results = repository.findSourcesNeedingUpdate()

            assertThat(results).isNotEmpty
            assertThat(results).allSatisfy { source ->
                assertThat(source.isActive).isTrue()
            }
            // Should be ordered by updated_at ASC
            val timestamps = results.mapNotNull { it.updatedAt }
            assertThat(timestamps).isSorted()
        }
    }

    @Nested
    inner class ConstraintValidation {

        @Test
        fun `save with valid source types succeeds`() {
            listOf("HABR", "VCRU", "TELEGRAM", "RSS").forEach { type ->
                val source = Source.newSource(
                    sourceType = type,
                    name = "Test $type",
                    url = null,
                    updateFrequencyMinutes = 60,
                    isActive = true,
                    parameters = "{}"
                )
                val saved = repository.save(source)
                assertThat(saved.id).isNotNull()
                repository.deleteById(saved.id!!)
            }
        }

        @Test
        fun `save with invalid source type throws exception`() {
            val source = Source.newSource(
                sourceType = "INVALID",
                name = "Bad Source",
                url = null,
                updateFrequencyMinutes = 60,
                isActive = true,
                parameters = "{}"
            )

            org.junit.jupiter.api.assertThrows<org.springframework.data.relational.core.conversion.DbActionExecutionException> {
                repository.save(source)
            }
        }

        @Test
        fun `save with zero frequency throws exception`() {
            val source = Source.newSource(
                sourceType = "HABR",
                name = "Zero Freq Source",
                url = null,
                updateFrequencyMinutes = 0,
                isActive = true,
                parameters = "{}"
            )

            org.junit.jupiter.api.assertThrows<org.springframework.data.relational.core.conversion.DbActionExecutionException> {
                repository.save(source)
            }
        }
    }
}
