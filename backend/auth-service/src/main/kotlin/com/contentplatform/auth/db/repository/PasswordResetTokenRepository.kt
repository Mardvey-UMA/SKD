package com.contentplatform.auth.db.repository

import com.contentplatform.auth.db.repository.model.PasswordResetTokenEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PasswordResetTokenRepository : CrudRepository<PasswordResetTokenEntity, String>
