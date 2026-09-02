package com.contentplatform.auth.db.repository

import com.contentplatform.auth.db.repository.model.RefreshTokenEntity
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RefreshTokenRepository : CrudRepository<RefreshTokenEntity, UUID> {

    @Query("SELECT * FROM refresh_tokens WHERE token_hash = :tokenHash")
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    @Modifying
    @Query("DELETE FROM refresh_tokens WHERE user_id = :userId")
    fun deleteByUserId(userId: UUID)

    @Modifying
    @Query("DELETE FROM refresh_tokens WHERE expires_at < now()")
    fun deleteExpired(): Int
}
