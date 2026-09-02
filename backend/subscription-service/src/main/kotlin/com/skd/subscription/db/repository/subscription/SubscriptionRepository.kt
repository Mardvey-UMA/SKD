package com.skd.subscription.db.repository.subscription

import com.skd.subscription.db.repository.subscription.model.Subscription
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface SubscriptionRepository : CrudRepository<Subscription, UUID> {

    @Query("SELECT * FROM subscriptions WHERE user_id = :userId")
    fun findByUserId(userId: UUID): Subscription?

    @Query("SELECT * FROM subscriptions WHERE status = 'active' AND expires_at < :now")
    fun findExpiredActive(now: Instant): List<Subscription>

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE status = 'active'
          AND auto_renew = TRUE
          AND expires_at >= :now
          AND expires_at <= :deadline
        """
    )
    fun findExpiringWithAutoRenew(now: Instant, deadline: Instant): List<Subscription>
}
