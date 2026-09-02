package com.contentagg.config.db.repository.source

import com.contentagg.config.db.repository.source.model.Source
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

/**
 * Spring Data JDBC repository for Source entity.
 * Extends PagingAndSortingRepository for pagination support and CrudRepository for basic CRUD.
 */
@Repository
interface SourceRepository : PagingAndSortingRepository<Source, UUID>, CrudRepository<Source, UUID> {

    // Derived query methods
    fun findByIsActiveTrue(): List<Source>

    fun findBySourceType(sourceType: String): List<Source>

    // Custom queries with @Query for complex operations
    @Query("SELECT * FROM config.sources WHERE source_type = :sourceType AND is_active = true")
    fun findActiveBySourceType(sourceType: String): List<Source>

    @Query("SELECT * FROM config.sources WHERE source_type = :sourceType AND name = :name")
    fun findByTypeAndName(sourceType: String, name: String): Source?

    @Query("SELECT COUNT(*) > 0 FROM config.sources WHERE source_type = :sourceType AND name = :name")
    fun existsByTypeAndName(sourceType: String, name: String): Boolean

    @Query("SELECT * FROM config.sources WHERE name ILIKE '%' || :name || '%'")
    fun searchByName(name: String): List<Source>

    @Query("SELECT COUNT(*) > 0 FROM config.sources WHERE url = :url AND id != :excludeId")
    fun existsByUrlExcludingId(url: String, excludeId: UUID): Boolean

    /**
     * Find sources that need to be updated based on their update frequency.
     * Returns active sources where updated_at is older than update_frequency_minutes ago.
     */
    @Query("SELECT * FROM config.sources WHERE is_active = true AND updated_at < NOW() - (update_frequency_minutes * interval '1 minute') ORDER BY updated_at ASC")
    fun findSourcesNeedingUpdate(): List<Source>

    /**
     * Keyset-paginated catalog query for public `GET /api/sources` (Phase 2).
     * Order: (created_at DESC, id DESC). Cursor pair: (cursorCreatedAt, cursorId) — exclusive.
     * When cursorCreatedAt is NULL we fetch from the beginning.
     * LIMIT = :pageSize + 1 — caller detects `has_more` from the extra row.
     *
     * Note: `:q` is matched via ILIKE '%q%'. The pg_trgm GIN index (gin_trgm_ops) accelerates
     * substring ILIKE for queries >= 3 chars. Shorter queries fall back to seq scan — acceptable
     * given the small size of the sources table.
     */
    @Query(
        """
        SELECT * FROM config.sources
         WHERE (CAST(:type AS TEXT) IS NULL OR source_type = CAST(:type AS TEXT))
           AND (CAST(:q AS TEXT) IS NULL OR name ILIKE '%' || CAST(:q AS TEXT) || '%')
           AND (
                CAST(:cursorCreatedAt AS TIMESTAMP) IS NULL
                OR created_at < CAST(:cursorCreatedAt AS TIMESTAMP)
                OR (created_at = CAST(:cursorCreatedAt AS TIMESTAMP) AND id < CAST(:cursorId AS UUID))
           )
         ORDER BY created_at DESC, id DESC
         LIMIT :pageSize
        """
    )
    fun findCatalogPage(
        @Param("type") type: String?,
        @Param("q") q: String?,
        @Param("cursorCreatedAt") cursorCreatedAt: LocalDateTime?,
        @Param("cursorId") cursorId: UUID?,
        @Param("pageSize") pageSize: Int,
    ): List<Source>
}
