package com.contentplatform.user.unit.service

import com.contentplatform.user.application.service.ProfileService
import com.contentplatform.user.application.service.OutboxService
import com.contentplatform.user.db.repository.ProfileRepository
import com.contentplatform.user.db.repository.model.ProfileEntity
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Tag("unit")
class ProfileServiceTest {

    private val profileRepository = mockk<ProfileRepository>()
    private val outboxService = mockk<OutboxService>(relaxed = true)
    private val profileService = ProfileService(profileRepository, outboxService, SimpleMeterRegistry())

    @Nested
    inner class `createProfileFromRegistration` {

        @Test
        fun `should save profile with correct fields via repository`() {
            val userId = UUID.randomUUID()
            val email = "newuser@example.com"

            every { profileRepository.saveIgnoreConflict(any(), any(), any(), any(), any(), any(), any()) } returns Unit

            profileService.createProfileFromRegistration(userId, email)

            verify(exactly = 1) {
                profileRepository.saveIgnoreConflict(
                    id = userId,
                    email = email,
                    displayName = null,
                    avatarUrl = null,
                    subscriptionTier = "free",
                    onboardingCompleted = false,
                    categories = null
                )
            }
        }

        @Test
        fun `should create outbox event for user created`() {
            val userId = UUID.randomUUID()
            val email = "newuser@example.com"

            every { profileRepository.saveIgnoreConflict(any(), any(), any(), any(), any(), any(), any()) } returns Unit

            profileService.createProfileFromRegistration(userId, email)

            verify(exactly = 1) {
                outboxService.createUserCreatedEvent(userId, email)
            }
        }

        @Test
        fun `should call repository before outbox event`() {
            val userId = UUID.randomUUID()
            val email = "newuser@example.com"
            val callOrder = mutableListOf<String>()

            every { profileRepository.saveIgnoreConflict(any(), any(), any(), any(), any(), any(), any()) } answers {
                callOrder.add("repository")
                Unit
            }
            every { outboxService.createUserCreatedEvent(any(), any()) } answers {
                callOrder.add("outbox")
                Unit
            }

            profileService.createProfileFromRegistration(userId, email)

            assertThat(callOrder).containsExactly("repository", "outbox")
        }
    }

    @Nested
    inner class `getProfile` {

        @Test
        fun `should return profile when found`() {
            val userId = UUID.randomUUID()
            val profile = ProfileEntity(
                id = userId,
                email = "found@example.com",
                displayName = "Found User",
                subscriptionTier = "free",
                createdAt = Instant.now()
            )
            every { profileRepository.findById(userId) } returns Optional.of(profile)

            val result = profileService.getProfile(userId)

            assertThat(result).isEqualTo(profile)
        }

        @Test
        fun `should throw NotFoundException when profile not found`() {
            val userId = UUID.randomUUID()
            every { profileRepository.findById(userId) } returns Optional.empty()

            assertThatThrownBy { profileService.getProfile(userId) }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessageContaining(userId.toString())
        }
    }

    @Nested
    inner class `updateProfile` {

        @Test
        fun `should delegate to repository with display name and avatar url`() {
            val userId = UUID.randomUUID()
            val displayName = "New Name"
            val avatarUrl = "https://cdn.example.com/avatar.jpg"

            every { profileRepository.updateProfile(any(), any(), any()) } returns Unit
            every { profileRepository.findById(userId) } returns Optional.of(
                ProfileEntity(id = userId, email = "user@example.com", displayName = displayName, avatarUrl = avatarUrl)
            )

            val result = profileService.updateProfile(userId, displayName, avatarUrl)

            verify(exactly = 1) {
                profileRepository.updateProfile(userId, displayName, avatarUrl)
            }
            assertThat(result.displayName).isEqualTo(displayName)
            assertThat(result.avatarUrl).isEqualTo(avatarUrl)
        }

        @Test
        fun `should allow null display name and avatar url`() {
            val userId = UUID.randomUUID()

            every { profileRepository.updateProfile(any(), any(), any()) } returns Unit
            every { profileRepository.findById(userId) } returns Optional.of(
                ProfileEntity(id = userId, email = "user@example.com")
            )

            profileService.updateProfile(userId, null, null)

            verify(exactly = 1) {
                profileRepository.updateProfile(userId, null, null)
            }
        }

        @Test
        fun `should return updated entity after update`() {
            val userId = UUID.randomUUID()
            val updatedProfile = ProfileEntity(
                id = userId,
                email = "user@example.com",
                displayName = "Updated",
                avatarUrl = "https://new-avatar.com/img.png"
            )

            every { profileRepository.updateProfile(any(), any(), any()) } returns Unit
            every { profileRepository.findById(userId) } returns Optional.of(updatedProfile)

            val result = profileService.updateProfile(userId, "Updated", "https://new-avatar.com/img.png")

            assertThat(result.id).isEqualTo(userId)
        }
    }

    @Nested
    inner class `updateSubscriptionTier` {

        @Test
        fun `should delegate to repository with correct parameters`() {
            val userId = UUID.randomUUID()
            val tier = "premium"

            every { profileRepository.updateSubscriptionTier(any(), any()) } returns Unit

            profileService.updateSubscriptionTier(userId, tier)

            verify(exactly = 1) {
                profileRepository.updateSubscriptionTier(userId, tier)
            }
        }

        @Test
        fun `should accept free tier`() {
            val userId = UUID.randomUUID()

            every { profileRepository.updateSubscriptionTier(any(), any()) } returns Unit

            profileService.updateSubscriptionTier(userId, "free")

            verify(exactly = 1) {
                profileRepository.updateSubscriptionTier(userId, "free")
            }
        }
    }

    @Nested
    inner class `completeOnboarding` {

        @Test
        fun `should update onboarding flag and categories`() {
            val userId = UUID.randomUUID()
            val categories = listOf("technology", "science", "business")

            every { profileRepository.updateOnboarding(any(), any(), any()) } returns Unit

            profileService.completeOnboarding(userId, categories)

            verify(exactly = 1) {
                profileRepository.updateOnboarding(
                    id = userId,
                    onboardingCompleted = true,
                    categories = any()
                )
            }
        }

        @Test
        fun `should serialize categories as JSON string`() {
            val userId = UUID.randomUUID()
            val categories = listOf("technology", "science", "business")
            val categoriesSlot = slot<String>()

            every { profileRepository.updateOnboarding(any(), any(), capture(categoriesSlot)) } returns Unit

            profileService.completeOnboarding(userId, categories)

            val capturedCategories = categoriesSlot.captured
            assertThat(capturedCategories).contains("technology")
            assertThat(capturedCategories).contains("science")
            assertThat(capturedCategories).contains("business")
        }

        @Test
        fun `should always set onboarding completed to true`() {
            val userId = UUID.randomUUID()
            val onboardingSlot = slot<Boolean>()

            every { profileRepository.updateOnboarding(any(), capture(onboardingSlot), any()) } returns Unit

            profileService.completeOnboarding(userId, listOf("a", "b", "c"))

            assertThat(onboardingSlot.captured).isTrue()
        }
    }
}
