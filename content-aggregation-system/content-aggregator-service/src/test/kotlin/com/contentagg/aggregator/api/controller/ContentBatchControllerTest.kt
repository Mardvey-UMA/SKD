package com.contentagg.aggregator.api.controller

import com.contentagg.aggregator.IntegrationTestBase
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@Sql(
    scripts = ["classpath:db/test-data.sql"],
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class ContentBatchControllerTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    companion object {
        private const val BATCH_URL = "/api/v1/content/batch"
        private const val ID_1 = "a0000000-0000-0000-0000-000000000001"
        private const val ID_2 = "a0000000-0000-0000-0000-000000000002"
        private const val ID_3 = "a0000000-0000-0000-0000-000000000003"
        private const val SOURCE_ID_1 = "c0000000-0000-0000-0000-000000000001"
    }

    private fun batchRequest(
        ids: List<String>,
        includeRelated: Boolean = false,
        relatedLimit: Int = 5
    ): String = objectMapper.writeValueAsString(
        mapOf(
            "ids" to ids,
            "include_related" to includeRelated,
            "related_limit" to relatedLimit
        )
    )

    @Nested
    @DisplayName("POST /batch basic behavior")
    inner class BasicBehaviorTests {

        @Test
        @DisplayName("returns items map keyed by UUID string for found IDs")
        fun foundIds_returnedAsMap() {
            mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest(listOf(ID_1, ID_2)))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.$ID_1").exists())
                .andExpect(jsonPath("$.items.$ID_2").exists())
                .andExpect(jsonPath("$.items.$ID_1.id").value(ID_1))
                .andExpect(jsonPath("$.items.$ID_2.id").value(ID_2))
        }

        @Test
        @DisplayName("not_found contains IDs absent from published_content")
        fun missingIds_inNotFound() {
            val missingId = "f0000000-0000-0000-0000-000000000099"
            mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest(listOf(ID_1, missingId)))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.$ID_1").exists())
                .andExpect(jsonPath("$.not_found[0]").value(missingId))
        }

        @Test
        @DisplayName("empty ids returns empty items and empty not_found")
        fun emptyIds_returnsEmpty() {
            val response = mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest(emptyList()))
            )
                .andExpect(status().isOk)
                .andReturn()

            val body = objectMapper.readTree(response.response.contentAsString)
            assertEquals(0, body.get("items").size())
            // not_found may be omitted by NON_EMPTY serialization when it's an empty list
            val notFound = body.get("not_found")
            assertTrue(notFound == null || notFound.size() == 0)
        }

        @Test
        @DisplayName("duplicate IDs in request handled — returned once")
        fun duplicateIds_returnedOnce() {
            val response = mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest(listOf(ID_1, ID_1, ID_1)))
            )
                .andExpect(status().isOk)
                .andReturn()

            val body = objectMapper.readTree(response.response.contentAsString)
            assertEquals(1, body.get("items").size())
        }

        @Test
        @DisplayName("response excludes internal fields (content_id, external_id, etc.) but includes source_id")
        fun internalFields_excluded() {
            mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest(listOf(ID_1)))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.$ID_1.content_id").doesNotExist())
                .andExpect(jsonPath("$.items.$ID_1.external_id").doesNotExist())
                .andExpect(jsonPath("$.items.$ID_1.dedup_article_id").doesNotExist())
                .andExpect(jsonPath("$.items.$ID_1.content_hash").doesNotExist())
                .andExpect(jsonPath("$.items.$ID_1.created_at").doesNotExist())
                .andExpect(jsonPath("$.items.$ID_1.updated_at").doesNotExist())
        }

        @Test
        @DisplayName("source_id is present and matches seeded UUID in batch response")
        fun sourceId_presentInBatchResponse() {
            mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest(listOf(ID_1)))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.$ID_1.source_id").value(SOURCE_ID_1))
        }

        @Test
        @DisplayName("source_id is present when include_related=false (default feed-service path)")
        fun sourceId_presentWithRelatedFalse() {
            mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest(listOf(ID_1), includeRelated = false))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.$ID_1.source_id").value(SOURCE_ID_1))
        }

        @Test
        @DisplayName("response includes required public fields")
        fun publicFields_included() {
            mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest(listOf(ID_1)))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.$ID_1.title").value("Kotlin coroutines tutorial"))
                .andExpect(jsonPath("$.items.$ID_1.source_type").value("HABR"))
                .andExpect(jsonPath("$.items.$ID_1.author_name").value("Author One"))
                .andExpect(jsonPath("$.items.$ID_1.url").value("https://habr.com/article/1"))
        }
    }

    @Nested
    @DisplayName("include_related=false behavior")
    inner class RelatedFalseTests {

        @Test
        @DisplayName("related_ids field is absent when include_related=false")
        fun relatedFalse_relatedIdsAbsent() {
            mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest(listOf(ID_1), includeRelated = false))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.$ID_1.related_ids").doesNotExist())
        }

        @Test
        @DisplayName("related_ids field is absent by default (no include_related in request)")
        fun relatedDefault_relatedIdsAbsent() {
            val body = objectMapper.writeValueAsString(mapOf("ids" to listOf(ID_1)))
            mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.$ID_1.related_ids").doesNotExist())
        }
    }

    @Nested
    @DisplayName("include_related=true behavior")
    inner class RelatedTrueTests {

        @Test
        @DisplayName("related_ids populated for items with dedup_article_id")
        fun relatedTrue_relatedIdsPopulated() {
            // article 1 is RELATED to article 2 (published as ID_2) and article 3 (published as ID_3)
            val response = mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest(listOf(ID_1), includeRelated = true))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.$ID_1.related_ids").isArray)
                .andReturn()

            val body = objectMapper.readTree(response.response.contentAsString)
            val relatedIds = body.get("items").get(ID_1).get("related_ids")
            assertNotNull(relatedIds)
            assertTrue(relatedIds.size() > 0)
        }

        @Test
        @DisplayName("related_ids is empty list for items with no dedup_article_id")
        fun relatedTrue_noArticleId_emptyList() {
            // ID_4 = vcru-001, dedup_article_id = NULL in test data
            val id4 = "a0000000-0000-0000-0000-000000000004"
            mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest(listOf(id4), includeRelated = true))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.$id4.related_ids").isArray)
                .andExpect(jsonPath("$.items.$id4.related_ids").isEmpty)
        }

        @Test
        @DisplayName("related_limit is respected")
        fun relatedLimit_respected() {
            // article 1 has 2 related (articles 2 and 3), limit=1 should return 1
            val response = mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest(listOf(ID_1), includeRelated = true, relatedLimit = 1))
            )
                .andExpect(status().isOk)
                .andReturn()

            val body = objectMapper.readTree(response.response.contentAsString)
            val relatedIds = body.get("items").get(ID_1).get("related_ids")
            assertNotNull(relatedIds)
            assertEquals(1, relatedIds.size())
        }

        @Test
        @DisplayName("related_ids does not include the item itself")
        fun relatedIds_noSelfReference() {
            val response = mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest(listOf(ID_1), includeRelated = true))
            )
                .andExpect(status().isOk)
                .andReturn()

            val body = objectMapper.readTree(response.response.contentAsString)
            val relatedIds = body.get("items").get(ID_1).get("related_ids")
            if (relatedIds != null && relatedIds.size() > 0) {
                assertFalse(relatedIds.any { it.asText() == ID_1 }, "related_ids must not contain the item itself")
            }
        }

        @Test
        @DisplayName("related IDs reference article 2 and article 3 from similarities")
        fun relatedIds_containsExpectedRelated() {
            val response = mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest(listOf(ID_1), includeRelated = true, relatedLimit = 10))
            )
                .andExpect(status().isOk)
                .andReturn()

            val body = objectMapper.readTree(response.response.contentAsString)
            val relatedIds = body.get("items").get(ID_1).get("related_ids").map { it.asText() }
            assertTrue(relatedIds.contains(ID_2) || relatedIds.contains(ID_3),
                "Expected related IDs to contain ID_2 or ID_3")
        }
    }

    @Nested
    @DisplayName("Validation")
    inner class ValidationTests {

        @Test
        @DisplayName("returns 4xx when ids list exceeds 100 items")
        fun tooManyIds_returns4xx() {
            val ids = (1..101).map { "a0000000-0000-0000-0000-${it.toString().padStart(12, '0')}" }
            mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest(ids))
            )
                .andExpect(status().is4xxClientError)
        }

        @Test
        @DisplayName("returns 4xx when related_limit exceeds 10")
        fun relatedLimitTooHigh_returns4xx() {
            mockMvc.perform(
                post(BATCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchRequest(listOf(ID_1), includeRelated = true, relatedLimit = 11))
            )
                .andExpect(status().is4xxClientError)
        }
    }
}
