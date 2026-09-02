package com.contentplatform.user.unit.db

import com.contentplatform.user.db.repository.model.ProfileEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("unit")
class ProfileEntityTest {

    @Test
    fun `should create profile with all fields`() {
        val id = UUID.randomUUID()
        val profile = ProfileEntity(
            id = id,
            email = "test@example.com",
            displayName = "Test User",
            avatarUrl = "https://example.com/avatar.png",
            subscriptionTier = "premium",
            onboardingCompleted = true,
            categories = """["tech","science"]"""
        )

        assertThat(profile.id).isEqualTo(id)
        assertThat(profile.email).isEqualTo("test@example.com")
        assertThat(profile.displayName).isEqualTo("Test User")
        assertThat(profile.avatarUrl).isEqualTo("https://example.com/avatar.png")
        assertThat(profile.subscriptionTier).isEqualTo("premium")
        assertThat(profile.onboardingCompleted).isTrue()
        assertThat(profile.categories).isEqualTo("""["tech","science"]""")
    }

    @Test
    fun `should create profile with defaults for optional fields`() {
        val id = UUID.randomUUID()
        val profile = ProfileEntity(
            id = id,
            email = "minimal@example.com"
        )

        assertThat(profile.id).isEqualTo(id)
        assertThat(profile.email).isEqualTo("minimal@example.com")
        assertThat(profile.displayName).isNull()
        assertThat(profile.avatarUrl).isNull()
        assertThat(profile.subscriptionTier).isEqualTo("free")
        assertThat(profile.onboardingCompleted).isFalse()
        assertThat(profile.categories).isNull()
        assertThat(profile.createdAt).isNull()
        assertThat(profile.updatedAt).isNull()
    }

    @Test
    fun `should support data class copy`() {
        val id = UUID.randomUUID()
        val original = ProfileEntity(id = id, email = "original@example.com")

        val updated = original.copy(
            displayName = "Updated Name",
            subscriptionTier = "premium"
        )

        assertThat(updated.id).isEqualTo(id)
        assertThat(updated.email).isEqualTo("original@example.com")
        assertThat(updated.displayName).isEqualTo("Updated Name")
        assertThat(updated.subscriptionTier).isEqualTo("premium")
    }

    @Test
    fun `id is explicitly set not auto-generated`() {
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val profile = ProfileEntity(id = id, email = "explicit@example.com")

        // id is a required parameter - not nullable, not auto-generated
        assertThat(profile.id).isEqualTo(id)
    }
}
