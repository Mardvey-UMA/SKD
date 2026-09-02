package com.contentplatform.auth.db.repository

import com.contentplatform.auth.db.repository.model.OutboxEventEntity
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface OutboxRepository : CrudRepository<OutboxEventEntity, Long> {

    @Query("SELECT * FROM outbox WHERE published_at IS NULL ORDER BY created_at LIMIT :limit")
    fun findUnpublished(limit: Int): List<OutboxEventEntity>

    @Modifying
    @Query("UPDATE outbox SET published_at = :publishedAt WHERE id = :id")
    fun markPublished(id: Long, publishedAt: Instant)
}
