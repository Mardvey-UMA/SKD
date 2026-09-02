package com.contentplatform.user.db.repository

import com.contentplatform.user.db.repository.model.ProfileEntity
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProfileRepository : CrudRepository<ProfileEntity, UUID> {

    @Modifying
    @Query(
        """
        INSERT INTO profiles (id, email, display_name, avatar_url, subscription_tier, onboarding_completed, categories)
        VALUES (:id, :email, :displayName, :avatarUrl, :subscriptionTier, :onboardingCompleted, :categories)
        ON CONFLICT (id) DO NOTHING
        """
    )
    fun saveIgnoreConflict(
        id: UUID,
        email: String,
        displayName: String?,
        avatarUrl: String?,
        subscriptionTier: String,
        onboardingCompleted: Boolean,
        categories: String?
    )

    @Modifying
    @Query("UPDATE profiles SET subscription_tier = :tier, updated_at = now() WHERE id = :id")
    fun updateSubscriptionTier(id: UUID, tier: String)

    @Modifying
    @Query(
        """
        UPDATE profiles
        SET onboarding_completed = :onboardingCompleted,
            categories = :categories,
            updated_at = now()
        WHERE id = :id
        """
    )
    fun updateOnboarding(id: UUID, onboardingCompleted: Boolean, categories: String)

    @Modifying
    @Query(
        """
        UPDATE profiles
        SET display_name = :displayName,
            avatar_url = :avatarUrl,
            updated_at = now()
        WHERE id = :id
        """
    )
    fun updateProfile(id: UUID, displayName: String?, avatarUrl: String?)
}
