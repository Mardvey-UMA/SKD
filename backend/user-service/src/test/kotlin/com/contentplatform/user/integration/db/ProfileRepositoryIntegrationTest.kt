package com.contentplatform.user.integration.db

import com.contentplatform.user.db.repository.ProfileRepository
import com.contentplatform.user.db.repository.model.ProfileEntity
import com.contentplatform.user.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

@Tag("integration")
class ProfileRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var profileRepository: ProfileRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM outbox")
        jdbcTemplate.execute("DELETE FROM profiles")
    }

    @Nested
    inner class `save and findById` {

        @Test
        fun `should save profile and find by id`() {
            val id = UUID.randomUUID()
            val profile = ProfileEntity(
                id = id,
                email = "test@example.com",
                displayName = "Test User",
                avatarUrl = "https://example.com/avatar.png"
            )

            profileRepository.save(profile)

            val found = profileRepository.findById(id)
            assertThat(found).isPresent
            assertThat(found.get().email).isEqualTo("test@example.com")
            assertThat(found.get().displayName).isEqualTo("Test User")
            assertThat(found.get().avatarUrl).isEqualTo("https://example.com/avatar.png")
        }

        @Test
        fun `should return empty optional when profile not found`() {
            val found = profileRepository.findById(UUID.randomUUID())
            assertThat(found).isEmpty()
        }
    }

    @Nested
    inner class `default values` {

        @Test
        fun `subscription_tier defaults to free`() {
            val id = UUID.randomUUID()
            profileRepository.save(
                ProfileEntity(
                    id = id,
                    email = "defaults@example.com"
                )
            )

            val found = profileRepository.findById(id)
            assertThat(found).isPresent
            assertThat(found.get().subscriptionTier).isEqualTo("free")
        }

        @Test
        fun `onboarding_completed defaults to false`() {
            val id = UUID.randomUUID()
            profileRepository.save(
                ProfileEntity(
                    id = id,
                    email = "defaults@example.com"
                )
            )

            val found = profileRepository.findById(id)
            assertThat(found).isPresent
            assertThat(found.get().onboardingCompleted).isFalse()
        }

        @Test
        fun `categories defaults to null`() {
            val id = UUID.randomUUID()
            profileRepository.save(
                ProfileEntity(
                    id = id,
                    email = "defaults@example.com"
                )
            )

            val found = profileRepository.findById(id)
            assertThat(found).isPresent
            assertThat(found.get().categories).isNull()
        }
    }

    @Nested
    inner class `saveIgnoreConflict (idempotent insert)` {

        @Test
        fun `should insert profile on first call`() {
            val id = UUID.randomUUID()
            profileRepository.saveIgnoreConflict(
                id = id,
                email = "idempotent@example.com",
                displayName = null,
                avatarUrl = null,
                subscriptionTier = "free",
                onboardingCompleted = false,
                categories = null
            )

            val found = profileRepository.findById(id)
            assertThat(found).isPresent
            assertThat(found.get().email).isEqualTo("idempotent@example.com")
        }

        @Test
        fun `should not throw on duplicate insert with same id`() {
            val id = UUID.randomUUID()

            profileRepository.saveIgnoreConflict(
                id = id,
                email = "first@example.com",
                displayName = null,
                avatarUrl = null,
                subscriptionTier = "free",
                onboardingCompleted = false,
                categories = null
            )

            // Second insert with same id -- should be silently ignored
            profileRepository.saveIgnoreConflict(
                id = id,
                email = "second@example.com",
                displayName = "Should Not Overwrite",
                avatarUrl = null,
                subscriptionTier = "premium",
                onboardingCompleted = true,
                categories = null
            )

            // Original values should be preserved
            val found = profileRepository.findById(id)
            assertThat(found).isPresent
            assertThat(found.get().email).isEqualTo("first@example.com")
            assertThat(found.get().subscriptionTier).isEqualTo("free")
            assertThat(found.get().onboardingCompleted).isFalse()
        }

        @Test
        fun `should allow different ids with same email`() {
            val id1 = UUID.randomUUID()
            val id2 = UUID.randomUUID()

            profileRepository.saveIgnoreConflict(
                id = id1,
                email = "same@example.com",
                displayName = null,
                avatarUrl = null,
                subscriptionTier = "free",
                onboardingCompleted = false,
                categories = null
            )

            profileRepository.saveIgnoreConflict(
                id = id2,
                email = "same@example.com",
                displayName = null,
                avatarUrl = null,
                subscriptionTier = "free",
                onboardingCompleted = false,
                categories = null
            )

            assertThat(profileRepository.findById(id1)).isPresent
            assertThat(profileRepository.findById(id2)).isPresent
        }
    }

    @Nested
    inner class `updateSubscriptionTier` {

        @Test
        fun `should update subscription tier for existing profile`() {
            val id = UUID.randomUUID()
            profileRepository.save(
                ProfileEntity(id = id, email = "tier@example.com")
            )

            profileRepository.updateSubscriptionTier(id, "premium")

            val found = profileRepository.findById(id)
            assertThat(found).isPresent
            assertThat(found.get().subscriptionTier).isEqualTo("premium")
        }

        @Test
        fun `should not throw when updating non-existent profile`() {
            // Should silently do nothing (0 rows updated)
            profileRepository.updateSubscriptionTier(UUID.randomUUID(), "premium")
        }
    }

    @Nested
    inner class `updateOnboarding` {

        @Test
        fun `should set onboarding_completed and categories`() {
            val id = UUID.randomUUID()
            profileRepository.save(
                ProfileEntity(id = id, email = "onboard@example.com")
            )

            val categoriesJson = """["technology","science","business"]"""
            profileRepository.updateOnboarding(id, true, categoriesJson)

            val found = profileRepository.findById(id)
            assertThat(found).isPresent
            assertThat(found.get().onboardingCompleted).isTrue()
            assertThat(found.get().categories).isEqualTo(categoriesJson)
        }
    }

    @Nested
    inner class `updateProfile` {

        @Test
        fun `should update display_name and avatar_url`() {
            val id = UUID.randomUUID()
            profileRepository.save(
                ProfileEntity(id = id, email = "update@example.com")
            )

            profileRepository.updateProfile(id, "New Name", "https://example.com/new-avatar.png")

            val found = profileRepository.findById(id)
            assertThat(found).isPresent
            assertThat(found.get().displayName).isEqualTo("New Name")
            assertThat(found.get().avatarUrl).isEqualTo("https://example.com/new-avatar.png")
        }
    }
}
