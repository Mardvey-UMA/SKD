package com.contentplatform.feed.unit.api

import com.contentplatform.feed.api.controller.SpaceController
import com.contentplatform.feed.api.exception.DuplicateSpaceNameException
import com.contentplatform.feed.api.exception.GlobalExceptionHandler
import com.contentplatform.feed.api.exception.NotFoundException
import com.contentplatform.feed.api.exception.SpaceLimitReachedException
import com.contentplatform.feed.application.SpaceService
import com.contentplatform.feed.application.dto.SpaceListResponse
import com.contentplatform.feed.application.dto.SpaceResponse
import com.contentplatform.feed.configuration.GatewayProperties
import com.contentplatform.feed.configuration.InternalSecurityProperties
import com.contentplatform.feed.security.GatewaySignatureFilter
import com.contentplatform.feed.security.InternalSignatureFilter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("unit")
@WebMvcTest(SpaceController::class)
@Import(
    GatewaySignatureFilter::class,
    InternalSignatureFilter::class,
    GatewayProperties::class,
    InternalSecurityProperties::class,
    GlobalExceptionHandler::class
)
class SpaceControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockkBean lateinit var spaceService: SpaceService

    @Autowired lateinit var gatewayProperties: GatewayProperties

    private val mapper = ObjectMapper().registerKotlinModule()

    private fun hmac(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    private fun headers(userId: String, requestId: String = UUID.randomUUID().toString()): Map<String, String> {
        val payload = "$userId|USER|free|$requestId"
        return mapOf(
            "X-User-Id" to userId,
            "X-User-Roles" to "USER",
            "X-Subscription-Tier" to "free",
            "X-Request-Id" to requestId,
            "X-Gateway-Signature" to hmac(payload, gatewayProperties.hmacSecret)
        )
    }

    @Test
    fun `GET api feed spaces returns list`() {
        val userId = UUID.randomUUID().toString()
        every { spaceService.list(UUID.fromString(userId)) } returns SpaceListResponse(
            items = emptyList(), count = 0, limit = 10
        )

        val req = get("/api/feed/spaces")
        headers(userId).forEach { (k, v) -> req.header(k, v) }

        mockMvc.perform(req)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.limit").value(10))
    }

    @Test
    fun `POST api feed spaces returns 201`() {
        val userId = UUID.randomUUID().toString()
        val id = UUID.randomUUID()
        val now = Instant.now()
        every { spaceService.create(UUID.fromString(userId), any()) } returns SpaceResponse(
            id = id, name = "Kotlin", color = "BLUE",
            sourceIds = emptyList(), sourceCount = 0,
            createdAt = now, updatedAt = now
        )

        val body = mapper.writeValueAsString(mapOf("name" to "Kotlin", "color" to "BLUE", "source_ids" to emptyList<String>()))
        val req = post("/api/feed/spaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
        headers(userId).forEach { (k, v) -> req.header(k, v) }

        mockMvc.perform(req)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.color").value("BLUE"))
    }

    @Test
    fun `POST api feed spaces returns 409 space_limit_reached on 11th`() {
        val userId = UUID.randomUUID().toString()
        every { spaceService.create(UUID.fromString(userId), any()) } throws SpaceLimitReachedException()

        val body = mapper.writeValueAsString(mapOf("name" to "X", "color" to "BLUE", "source_ids" to emptyList<String>()))
        val req = post("/api/feed/spaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
        headers(userId).forEach { (k, v) -> req.header(k, v) }

        mockMvc.perform(req)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("space_limit_reached"))
    }

    @Test
    fun `POST api feed spaces returns 409 duplicate_space_name`() {
        val userId = UUID.randomUUID().toString()
        every { spaceService.create(UUID.fromString(userId), any()) } throws DuplicateSpaceNameException()

        val body = mapper.writeValueAsString(mapOf("name" to "X", "color" to "BLUE", "source_ids" to emptyList<String>()))
        val req = post("/api/feed/spaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
        headers(userId).forEach { (k, v) -> req.header(k, v) }

        mockMvc.perform(req)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("duplicate_space_name"))
    }

    @Test
    fun `GET api feed spaces id returns 404 space_not_found`() {
        val userId = UUID.randomUUID().toString()
        val spaceId = UUID.randomUUID()
        every { spaceService.getById(UUID.fromString(userId), spaceId) } throws NotFoundException("Space not found: $spaceId")

        val req = get("/api/feed/spaces/$spaceId")
        headers(userId).forEach { (k, v) -> req.header(k, v) }

        mockMvc.perform(req)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("space_not_found"))
    }

    @Test
    fun `DELETE api feed spaces id returns 204`() {
        val userId = UUID.randomUUID().toString()
        val spaceId = UUID.randomUUID()
        every { spaceService.delete(UUID.fromString(userId), spaceId) } returns Unit

        val req = delete("/api/feed/spaces/$spaceId")
        headers(userId).forEach { (k, v) -> req.header(k, v) }

        mockMvc.perform(req).andExpect(status().isNoContent)
    }
}
