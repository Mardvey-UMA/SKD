package com.skd.userinteractions.repository

import com.skd.userinteractions.domain.UserInteraction
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.util.UUID

interface UserInteractionRepository : CrudRepository<UserInteraction, Long> {

    @Query("SELECT * FROM user_interactions WHERE user_id = :userId")
    fun findByUserId(userId: UUID): List<UserInteraction>
}
