package com.skd.subscription.db.repository.outbox

import com.skd.subscription.db.repository.outbox.model.OutboxEntry
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface OutboxRepository : CrudRepository<OutboxEntry, Long> {

    override fun <S : OutboxEntry> save(entity: S): S

    @Query("SELECT * FROM outbox WHERE published_at IS NULL ORDER BY created_at LIMIT :limit")
    fun findUnpublished(limit: Int): List<OutboxEntry>

    @Modifying
    @Query("UPDATE outbox SET published_at = :publishedAt WHERE id = :id")
    fun markPublished(id: Long, publishedAt: Instant)

    @Modifying
    @Query("DELETE FROM outbox WHERE published_at < :cutoff")
    fun deletePublishedBefore(cutoff: Instant): Int
}
