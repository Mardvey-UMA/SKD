package com.contentplatform.auth.db.repository.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("users")
data class UserEntity(
    @Id val id: UUID? = null,
    val email: String,
    val passwordHash: String,
    val emailVerified: Boolean = false,
    val subscriptionTier: String = "free",
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null
)
