package com.contentplatform.auth.db.repository.model

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("password_reset_tokens")
class PasswordResetTokenEntity(
    @Id val token: String,
    val userId: UUID,
    val expiresAt: Instant,
    val used: Boolean = false,
    val createdAt: Instant? = null
) : Persistable<String> {

    @Transient
    private var newEntity: Boolean = false

    override fun getId(): String = token
    override fun isNew(): Boolean = newEntity

    fun markNew(): PasswordResetTokenEntity {
        this.newEntity = true
        return this
    }

    fun copy(
        token: String = this.token,
        userId: UUID = this.userId,
        expiresAt: Instant = this.expiresAt,
        used: Boolean = this.used,
        createdAt: Instant? = this.createdAt
    ): PasswordResetTokenEntity = PasswordResetTokenEntity(token, userId, expiresAt, used, createdAt)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasswordResetTokenEntity) return false
        return token == other.token
    }

    override fun hashCode(): Int = token.hashCode()

    override fun toString(): String =
        "PasswordResetTokenEntity(token=$token, userId=$userId, expiresAt=$expiresAt, used=$used, createdAt=$createdAt)"
}
