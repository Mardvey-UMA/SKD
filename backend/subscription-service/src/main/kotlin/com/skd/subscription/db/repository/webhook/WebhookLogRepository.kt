package com.skd.subscription.db.repository.webhook

import com.skd.subscription.db.repository.webhook.model.WebhookLog
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface WebhookLogRepository : CrudRepository<WebhookLog, String> {

    @Query("SELECT COUNT(*) > 0 FROM webhook_log WHERE external_event_id = :externalEventId")
    fun existsByExternalEventId(externalEventId: String): Boolean

    @Modifying
    @Query("DELETE FROM webhook_log WHERE received_at < :cutoff")
    fun deleteReceivedBefore(cutoff: Instant): Int
}
