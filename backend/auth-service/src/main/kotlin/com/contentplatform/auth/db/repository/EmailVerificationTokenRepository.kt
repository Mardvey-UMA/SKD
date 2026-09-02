package com.contentplatform.auth.db.repository

import com.contentplatform.auth.db.repository.model.EmailVerificationTokenEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface EmailVerificationTokenRepository : CrudRepository<EmailVerificationTokenEntity, String>
