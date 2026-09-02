package com.contentplatform.feed.unit.api

import com.contentplatform.feed.api.controller.BookmarkController
import com.contentplatform.feed.api.exception.GlobalExceptionHandler
import com.contentplatform.feed.application.CollectionService
import com.contentplatform.feed.application.exception.AlreadyExistsException
import com.contentplatform.feed.configuration.GatewayProperties
import com.contentplatform.feed.infrastructure.client.model.ContentBatchItem
import com.contentplatform.feed.application.dto.CollectionPageResponse
import com.contentplatform.feed.security.GatewaySignatureFilter
import com.contentplatform.feed.security.InternalSignatureFilter
import com.contentplatform.feed.configuration.InternalSecurityProperties
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("unit")
@WebMvcTest(BookmarkController::class)
@Import(GatewaySignatureFilter::class, InternalSignatureFilter::class, GatewayProperties::class, InternalSecurityProperties::class, GlobalExceptionHandler::class)
class BookmarkControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var collectionService: CollectionService

    @Autowired
    lateinit var gatewayProperties: GatewayProperties

    private fun computeHmac(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    private fun authenticatedRequest(
        method: String,
        uri: String,
        userId: String = UUID.randomUUID().toString(),
        roles: String = "USER",
        tier: String = "free",
        requestId: String = UUID.randomUUID().toString()
    ): org.springframework.test.web.servlet.RequestBuilder {
        val payload = "$userId|$roles|$tier|$requestId"
        val signature = computeHmac(payload, gatewayProperties.hmacSecret)
        val builder = when (method) {
            "POST" -> post(uri)
            "DELETE" -> delete(uri)
            "GET" -> get(uri)
            else -> get(uri)
        }
        return builder
            .header("X-User-Id", userId)
            .header("X-User-Roles", roles)
            .header("X-Subscription-Tier", tier)
            .header("X-Request-Id", requestId)
            .header("X-Gateway-Signature", signature)
    }

    @Nested
    inner class `POST api feed bookmarks` {

        @Test
        fun `should return 201 when bookmark added successfully`() {
            val userId = UUID.randomUUID().toString()
            val contentId = UUID.randomUUID()

            every { collectionService.addBookmark(UUID.fromString(userId), contentId) } just runs

            mockMvc.perform(authenticatedRequest("POST", "/api/feed/bookmarks/$contentId", userId = userId))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.content_id", `is`(contentId.toString())))
                .andExpect(jsonPath("$.action", `is`("bookmarked")))
        }

        @Test
        fun `should return 409 when bookmark already exists`() {
            val userId = UUID.randomUUID().toString()
            val contentId = UUID.randomUUID()

            every { collectionService.addBookmark(UUID.fromString(userId), contentId) } throws
                AlreadyExistsException("Bookmark already exists")

            mockMvc.perform(authenticatedRequest("POST", "/api/feed/bookmarks/$contentId", userId = userId))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.error", `is`("conflict")))
        }
    }

    @Nested
    inner class `DELETE api feed bookmarks` {

        @Test
        fun `should return 200 when bookmark removed successfully`() {
            val userId = UUID.randomUUID().toString()
            val contentId = UUID.randomUUID()

            every { collectionService.removeBookmark(UUID.fromString(userId), contentId) } just runs

            mockMvc.perform(authenticatedRequest("DELETE", "/api/feed/bookmarks/$contentId", userId = userId))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content_id", `is`(contentId.toString())))
                .andExpect(jsonPath("$.action", `is`("unbookmarked")))
        }
    }

    @Nested
    inner class `GET api feed bookmarks` {

        @Test
        fun `should return 200 with paginated bookmark list`() {
            val userId = UUID.randomUUID().toString()
            val item = ContentBatchItem(id = "content-1", title = "Bookmarked Article")
            val pageResponse = CollectionPageResponse(
                items = listOf(item),
                cursor = "next",
                hasNext = true
            )

            every { collectionService.getBookmarks(UUID.fromString(userId), null) } returns pageResponse

            mockMvc.perform(authenticatedRequest("GET", "/api/feed/bookmarks", userId = userId))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items", hasSize<Any>(1)))
                .andExpect(jsonPath("$.items[0].id", `is`("content-1")))
                .andExpect(jsonPath("$.hasNext", `is`(true)))
        }
    }
}
