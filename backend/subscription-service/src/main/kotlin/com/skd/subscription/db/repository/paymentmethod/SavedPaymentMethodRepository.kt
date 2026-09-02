package com.skd.subscription.db.repository.paymentmethod

import com.skd.subscription.db.repository.paymentmethod.model.SavedPaymentMethod
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SavedPaymentMethodRepository : CrudRepository<SavedPaymentMethod, UUID> {

    @Query("SELECT * FROM saved_payment_methods WHERE user_id = :userId AND active = TRUE LIMIT 1")
    fun findActiveByUserId(userId: UUID): SavedPaymentMethod?
}
