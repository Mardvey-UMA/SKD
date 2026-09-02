package com.contentplatform.user.db.repository.model

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("profiles")
class ProfileEntity(
    @get:JvmName("getEntityId")
    @Id val id: UUID,
    val email: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val subscriptionTier: String = "free",
    val onboardingCompleted: Boolean = false,
    val categories: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null
) : Persistable<UUID> {

    @Transient
    private var newEntity: Boolean = (createdAt == null)

    override fun getId(): UUID = id
    override fun isNew(): Boolean = newEntity

    fun markNew(): ProfileEntity {
        this.newEntity = true
        return this
    }

    fun copy(
        id: UUID = this.id,
        email: String = this.email,
        displayName: String? = this.displayName,
        avatarUrl: String? = this.avatarUrl,
        subscriptionTier: String = this.subscriptionTier,
        onboardingCompleted: Boolean = this.onboardingCompleted,
        categories: String? = this.categories,
        createdAt: Instant? = this.createdAt,
        updatedAt: Instant? = this.updatedAt
    ): ProfileEntity = ProfileEntity(
        id = id,
        email = email,
        displayName = displayName,
        avatarUrl = avatarUrl,
        subscriptionTier = subscriptionTier,
        onboardingCompleted = onboardingCompleted,
        categories = categories,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProfileEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "ProfileEntity(id=$id, email=$email, displayName=$displayName, subscriptionTier=$subscriptionTier, onboardingCompleted=$onboardingCompleted)"
}
