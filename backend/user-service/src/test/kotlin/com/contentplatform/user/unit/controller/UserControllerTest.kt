package com.contentplatform.user.unit.controller

import com.contentplatform.user.api.controller.UserController
import com.contentplatform.user.api.dto.OnboardingRequest
import com.contentplatform.user.api.dto.OnboardingResponse
import com.contentplatform.user.api.dto.ProfileResponse
import com.contentplatform.user.api.dto.UpdateProfileRequest
import com.contentplatform.user.api.handler.GlobalExceptionHandler
import com.contentplatform.user.application.exception.NotFoundException
import com.contentplatform.user.application.exception.OnboardingAlreadyCompletedException
import com.contentplatform.user.application.service.ProfileService
import com.contentplatform.user.db.repository.model.ProfileEntity
import com.contentplatform.user.infrastructure.client.RecSystemClient
import com.fasterxml.jackson.databind.ObjectMapper
import com.contentplatform.user.infrastructure.client.RecSystemException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@Tag("unit")
@WebMvcTest(controllers = [UserController::class])
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@Import(UserControllerTest.MockConfig::class)
class UserControllerTest {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun profileService(): ProfileService = mockk()

        @Bean
        fun recSystemClient(): RecSystemClient = mockk()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var profileService: ProfileService

    @Autowired
    lateinit var recSystemClient: RecSystemClient

    private val objectMapper = ObjectMapper()

    companion object {
        private val TEST_USER_ID = UUID.randomUUID()
        private val TEST_PROFILE = ProfileEntity(
            id = TEST_USER_ID,
            email = "test@example.com",
            displayName = "Test User",
            avatarUrl = "https://cdn.example.com/avatar.jpg",
            subscriptionTier = "free",
            onboardingCompleted = false,
            createdAt = Instant.parse("2026-04-01T10:00:00Z")
        )
    }

    @Nested
    inner class `GET api users me` {

        @Test
        fun `should return 200 with profile response when user exists`() {
            every { profileService.getProfile(TEST_USER_ID) } returns TEST_PROFILE

            mockMvc.perform(
                get("/api/users/me")
                    .requestAttr("X-User-Id", TEST_USER_ID.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(TEST_USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.display_name").value("Test User"))
                .andExpect(jsonPath("$.avatar_url").value("https://cdn.example.com/avatar.jpg"))
                .andExpect(jsonPath("$.subscription_tier").value("free"))
                .andExpect(jsonPath("$.onboarding_completed").value(false))
                .andExpect(jsonPath("$.created_at").isNotEmpty)
        }

        @Test
        fun `should return 404 when profile not found`() {
            every { profileService.getProfile(TEST_USER_ID) } throws NotFoundException("Profile not found for userId=$TEST_USER_ID")

            mockMvc.perform(
                get("/api/users/me")
                    .requestAttr("X-User-Id", TEST_USER_ID.toString())
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty)
        }
    }

    @Nested
    inner class `PUT api users me` {

        @Test
        fun `should return 200 with updated profile when request is valid`() {
            val updatedProfile = ProfileEntity(
                id = TEST_USER_ID,
                email = "test@example.com",
                displayName = "New Name",
                avatarUrl = "https://cdn.example.com/new-avatar.jpg",
                subscriptionTier = "free",
                onboardingCompleted = false,
                createdAt = Instant.parse("2026-04-01T10:00:00Z")
            )
            every { profileService.updateProfile(TEST_USER_ID, "New Name", "https://cdn.example.com/new-avatar.jpg") } returns updatedProfile

            val request = UpdateProfileRequest(
                displayName = "New Name",
                avatarUrl = "https://cdn.example.com/new-avatar.jpg"
            )

            mockMvc.perform(
                put("/api/users/me")
                    .requestAttr("X-User-Id", TEST_USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.display_name").value("New Name"))
                .andExpect(jsonPath("$.avatar_url").value("https://cdn.example.com/new-avatar.jpg"))
        }

        @Test
        fun `should return 200 when body has all null fields`() {
            every { profileService.updateProfile(TEST_USER_ID, null, null) } returns TEST_PROFILE

            mockMvc.perform(
                put("/api/users/me")
                    .requestAttr("X-User-Id", TEST_USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class `POST api users me onboarding` {

        @Test
        fun `should return 200 with onboarding response when categories are valid`() {
            val categories = listOf("technology", "science", "business")
            every { profileService.getProfile(TEST_USER_ID) } returns TEST_PROFILE
            every { profileService.completeOnboarding(TEST_USER_ID, categories) } returns Unit
            every { recSystemClient.postOnboarding(TEST_USER_ID, categories, emptyList()) } returns mapOf(
                "user_id" to TEST_USER_ID.toString(),
                "profile_initialized" to true
            )

            val request = OnboardingRequest(
                categories = categories,
                sourceContentIds = emptyList()
            )

            mockMvc.perform(
                post("/api/users/me/onboarding")
                    .requestAttr("X-User-Id", TEST_USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.onboarding_completed").value(true))
                .andExpect(jsonPath("$.message").isNotEmpty)
        }

        @Test
        fun `should return 200 with current state when onboarding already completed`() {
            val completedProfile = ProfileEntity(
                id = TEST_USER_ID,
                email = "test@example.com",
                onboardingCompleted = true,
                categories = "[\"technology\",\"science\",\"business\"]",
                createdAt = Instant.parse("2026-04-01T10:00:00Z")
            )
            every { profileService.getProfile(TEST_USER_ID) } returns completedProfile

            val request = OnboardingRequest(
                categories = listOf("technology", "science", "business"),
                sourceContentIds = emptyList()
            )

            mockMvc.perform(
                post("/api/users/me/onboarding")
                    .requestAttr("X-User-Id", TEST_USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.onboarding_completed").value(true))
                .andExpect(jsonPath("$.message").value("Already onboarded"))
        }

        @Test
        fun `should call rec-system FIRST before updating DB`() {
            val categories = listOf("technology", "science", "business")
            val callOrder = mutableListOf<String>()

            every { profileService.getProfile(TEST_USER_ID) } returns TEST_PROFILE
            every { recSystemClient.postOnboarding(TEST_USER_ID, categories, emptyList()) } answers {
                callOrder.add("rec-system")
                emptyMap()
            }
            every { profileService.completeOnboarding(TEST_USER_ID, categories) } answers {
                callOrder.add("db")
                Unit
            }

            mockMvc.perform(
                post("/api/users/me/onboarding")
                    .requestAttr("X-User-Id", TEST_USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(OnboardingRequest(categories = categories, sourceContentIds = emptyList())))
            )
                .andExpect(status().isOk)

            assertThat(callOrder).containsExactly("rec-system", "db")
        }

        @Test
        fun `should NOT update DB when rec-system call fails`() {
            val categories = listOf("technology", "science", "business")
            every { profileService.getProfile(TEST_USER_ID) } returns TEST_PROFILE
            every { recSystemClient.postOnboarding(TEST_USER_ID, categories, emptyList()) } throws RecSystemException("upstream error")

            mockMvc.perform(
                post("/api/users/me/onboarding")
                    .requestAttr("X-User-Id", TEST_USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(OnboardingRequest(categories = categories, sourceContentIds = emptyList())))
            )
                .andExpect(status().isBadGateway)

            verify(exactly = 0) { profileService.completeOnboarding(any(), any()) }
        }

        @Test
        fun `should return 422 when fewer than 3 categories provided`() {
            val request = OnboardingRequest(
                categories = listOf("technology", "science"),
                sourceContentIds = emptyList()
            )

            mockMvc.perform(
                post("/api/users/me/onboarding")
                    .requestAttr("X-User-Id", TEST_USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isUnprocessableEntity)
        }
    }

    @Nested
    inner class `GET api users me categories` {

        @Test
        fun `should return 200 with categories from rec-system`() {
            val categoriesJson = """{"categories":[{"id":"technology","name":"Technology","icon":"laptop"}],"min_select":3,"max_select":5}"""
            every { recSystemClient.getCategories("ru") } returns categoriesJson

            mockMvc.perform(
                get("/api/users/me/categories")
                    .requestAttr("X-User-Id", TEST_USER_ID.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.categories").isArray)
                .andExpect(jsonPath("$.min_select").value(3))
        }
    }
}
