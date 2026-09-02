package com.skd.subscription.db.repository.payment

import com.skd.subscription.db.repository.payment.model.Payment
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface PaymentRepository : CrudRepository<Payment, UUID> {

    @Query("SELECT * FROM payments WHERE external_id = :externalId LIMIT 1")
    fun findByExternalId(externalId: String): Payment?

    @Query(
        """
        SELECT * FROM payments
        WHERE user_id = :userId
          AND plan_id = :planId
          AND status = 'pending'
          AND created_at > :after
        LIMIT 1
        """
    )
    fun findPendingByUserAndPlan(userId: UUID, planId: String, after: Instant): Payment?

    @Query(
        """
        SELECT * FROM payments
        WHERE user_id = :userId
          AND status = 'pending'
          AND created_at > :after
        ORDER BY created_at DESC
        LIMIT 1
        """
    )
    fun findMostRecentPendingByUser(userId: UUID, after: Instant): Payment?

    @Query(
        """
        SELECT * FROM payments
        WHERE status = 'pending'
          AND created_at > :after
        ORDER BY created_at ASC
        """
    )
    fun findAllPendingCreatedAfter(after: Instant): List<Payment>

    @Modifying
    @Query("UPDATE payments SET status = 'succeeded', external_status = :externalStatus, updated_at = now() WHERE id = :paymentId AND status = 'pending'")
    fun markSucceededIfPending(paymentId: UUID, externalStatus: String): Int
}
