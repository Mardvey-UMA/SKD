package com.contentplatform.feed.unit.api

import com.contentplatform.feed.api.controller.FeedController
import com.contentplatform.feed.api.exception.GlobalExceptionHandler
import com.contentplatform.feed.api.exception.NotFoundException
import com.contentplatform.feed.application.CollectionService
import com.contentplatform.feed.application.FeedService
import com.contentplatform.feed.application.dto.ContentStatusResponse
import com.contentplatform.feed.application.dto.FeedResponse
import com.contentplatform.feed.configuration.GatewayProperties
import com.contentplatform.feed.infrastructure.client.model.ContentBatchItem
import com.contentplatform.feed.security.GatewaySignatureFilter
import com.contentplatform.feed.security.InternalSignatureFilter
import com.contentplatform.feed.configuration.InternalSecurityProperties
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("unit")
@WebMvcTest(FeedController::class)
@Import(GatewaySignatureFilter::class, InternalSignatureFilter::class, GatewayProperties::class, InternalSecurityProperties::class, GlobalExceptionHandler::class)
class FeedControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var feedService: FeedService

    @MockkBean
    lateinit var collectionService: CollectionService

    @Autowired
    lateinit var gatewayProperties: GatewayProperties

    private fun computeHmac(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    private fun authenticatedGet(
        uri: String,
        userId: String = UUID.randomUUID().toString(),
        roles: String = "USER",
        tier: String = "free",
        requestId: String = UUID.randomUUID().toString()
    ): org.springframework.test.web.servlet.RequestBuilder {
        val payload = "$userId|$roles|$tier|$requestId"
        val signature = computeHmac(payload, gatewayProperties.hmacSecret)
        return get(uri)
            .header("X-User-Id", userId)
            .header("X-User-Roles", roles)
            .header("X-Subscription-Tier", tier)
            .header("X-Request-Id", requestId)
            .header("X-Gateway-Signature", signature)
    }

    @Nested
    inner class `GET api feed` {

        @Test
        fun `should return 200 with feed response`() {
            val userId = UUID.randomUUID().toString()
            val item = ContentBatchItem(id = "content-1", title = "Test Article")
            val feedResponse = FeedResponse(
                items = listOf(item),
                cursor = "next-cursor",
                hasNext = true
            )

            every { feedService.getFeed(UUID.fromString(userId), null, false) } returns feedResponse

            mockMvc.perform(authenticatedGet("/api/feed", userId = userId))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items", hasSize<Any>(1)))
                .andExpect(jsonPath("$.items[0].id", `is`("content-1")))
                .andExpect(jsonPath("$.cursor", `is`("next-cursor")))
                .andExpect(jsonPath("$.hasNext", `is`(true)))
        }

        @Test
        fun `should pass refresh parameter to service`() {
            val userId = UUID.randomUUID().toString()
            val feedResponse = FeedResponse(items = emptyList(), cursor = null, hasNext = false)

            every { feedService.getFeed(UUID.fromString(userId), null, true) } returns feedResponse

            mockMvc.perform(authenticatedGet("/api/feed?refresh=true", userId = userId))
                .andExpect(status().isOk)

            verify { feedService.getFeed(UUID.fromString(userId), null, true) }
        }

        @Test
        fun `should pass cursor parameter to service`() {
            val userId = UUID.randomUUID().toString()
            val cursor = "abc123"
            val feedResponse = FeedResponse(items = emptyList(), cursor = null, hasNext = false)

            every { feedService.getFeed(UUID.fromString(userId), cursor, false) } returns feedResponse

            mockMvc.perform(authenticatedGet("/api/feed?cursor=$cursor", userId = userId))
                .andExpect(status().isOk)

            verify { feedService.getFeed(UUID.fromString(userId), cursor, false) }
        }
    }

    @Nested
    inner class `GET api feed content by id` {

        @Test
        fun `should return 200 with content item`() {
            val userId = UUID.randomUUID().toString()
            val contentId = "content-42"
            val item = ContentBatchItem(id = contentId, title = "Found Article")

            every { feedService.getContentById(contentId) } returns item

            mockMvc.perform(authenticatedGet("/api/feed/content/$contentId", userId = userId))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id", `is`(contentId)))
                .andExpect(jsonPath("$.title", `is`("Found Article")))
        }

        @Test
        fun `should return 404 when content not found`() {
            val userId = UUID.randomUUID().toString()
            val contentId = "nonexistent"

            every { feedService.getContentById(contentId) } returns null

            mockMvc.perform(authenticatedGet("/api/feed/content/$contentId", userId = userId))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class `GET api feed content status` {

        @Test
        fun `should return 200 with content status`() {
            val userId = UUID.randomUUID().toString()
            val contentId = UUID.randomUUID()
            val status = ContentStatusResponse(liked = true, disliked = false, bookmarked = true)

            every { collectionService.getContentStatus(UUID.fromString(userId), contentId) } returns status

            mockMvc.perform(authenticatedGet("/api/feed/content/$contentId/status", userId = userId))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.liked", `is`(true)))
                .andExpect(jsonPath("$.disliked", `is`(false)))
                .andExpect(jsonPath("$.bookmarked", `is`(true)))
        }
    }

    @Nested
    inner class `HMAC verification` {

        @Test
        fun `should return 403 when no gateway headers provided`() {
            mockMvc.perform(get("/api/feed"))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should return 403 when signature is invalid`() {
            mockMvc.perform(
                get("/api/feed")
                    .header("X-User-Id", UUID.randomUUID().toString())
                    .header("X-User-Roles", "USER")
                    .header("X-Subscription-Tier", "free")
                    .header("X-Request-Id", UUID.randomUUID().toString())
                    .header("X-Gateway-Signature", "wrong-signature")
            )
                .andExpect(status().isForbidden)
        }
    }
}
