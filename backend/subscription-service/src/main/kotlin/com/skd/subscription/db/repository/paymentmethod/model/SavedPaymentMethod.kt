package com.skd.subscription.db.repository.paymentmethod.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.util.UUID

@Table("saved_payment_methods")
data class SavedPaymentMethod(
    @Id
    val id: UUID? = null,
    @Column("user_id")
    val userId: UUID,
    @Column("yookassa_method_id")
    val yookassaMethodId: String,
    val type: String,
    @Column("card_last4")
    val cardLast4: String? = null,
    val active: Boolean = true
)
