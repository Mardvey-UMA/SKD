package com.contentplatform.auth.db.repository

import com.contentplatform.auth.db.repository.model.UserEntity
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRepository : CrudRepository<UserEntity, UUID> {

    @Query("SELECT * FROM users WHERE email = :email")
    fun findByEmail(email: String): UserEntity?
}
