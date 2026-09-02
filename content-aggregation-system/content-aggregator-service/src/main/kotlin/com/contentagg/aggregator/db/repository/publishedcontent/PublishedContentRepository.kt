package com.contentagg.aggregator.db.repository.publishedcontent

import com.contentagg.aggregator.db.repository.publishedcontent.model.PublishedContent
import com.contentagg.aggregator.db.repository.publishedcontent.model.RelatedContentRow
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PublishedContentRepository :
    PagingAndSortingRepository<PublishedContent, UUID>,
    CrudRepository<PublishedContent, UUID> {

    fun findBySourceType(sourceType: String, pageable: Pageable): Page<PublishedContent>

    fun countBySourceType(sourceType: String): Long

    @Query("SELECT * FROM published_content ORDER BY published_at DESC NULLS LAST LIMIT :size OFFSET :offset")
    fun findAllOrderByPublishedAtDescNullsLast(size: Int, offset: Long): List<PublishedContent>

    @Query("SELECT COUNT(*) FROM published_content")
    fun countAll(): Long

    @Query("SELECT * FROM published_content WHERE source_type = :sourceType AND (title ILIKE '%' || :search || '%' OR description ILIKE '%' || :search || '%') ORDER BY published_at DESC NULLS LAST LIMIT :size OFFSET :offset")
    fun searchBySourceType(sourceType: String, search: String, size: Int, offset: Long): List<PublishedContent>

    @Query("SELECT COUNT(*) FROM published_content WHERE source_type = :sourceType AND (title ILIKE '%' || :search || '%' OR description ILIKE '%' || :search || '%')")
    fun countBySourceTypeSearch(sourceType: String, search: String): Long

    @Query("SELECT * FROM published_content WHERE title ILIKE '%' || :search || '%' OR description ILIKE '%' || :search || '%' ORDER BY published_at DESC NULLS LAST LIMIT :size OFFSET :offset")
    fun searchByText(search: String, size: Int, offset: Long): List<PublishedContent>

    @Query("SELECT COUNT(*) FROM published_content WHERE title ILIKE '%' || :search || '%' OR description ILIKE '%' || :search || '%'")
    fun countByText(search: String): Long

    @Query("SELECT * FROM published_content WHERE source_type = :sourceType ORDER BY published_at DESC NULLS LAST LIMIT :size OFFSET :offset")
    fun findBySourceTypeOrdered(sourceType: String, size: Int, offset: Long): List<PublishedContent>

    @Query("SELECT * FROM published_content WHERE id = ANY(:ids)")
    fun findAllByIdIn(ids: Array<UUID>): List<PublishedContent>

    @Query("""
        SELECT DISTINCT ON (source_article_id, pc_related.id)
            CASE WHEN s.article_a = ANY(:articleIds) THEN s.article_a ELSE s.article_b END AS source_article_id,
            pc_related.id AS related_id,
            s.score
        FROM data_flow.similarities s
        JOIN data_flow.articles a_other
            ON a_other.id = CASE
                WHEN s.article_a = ANY(:articleIds) THEN s.article_b
                ELSE s.article_a
            END
        JOIN data_flow.published_content pc_related
            ON pc_related.content_id = a_other.raw_content_id
        WHERE (s.article_a = ANY(:articleIds) OR s.article_b = ANY(:articleIds))
          AND s.rel_type IN ('RELATED', 'DUPLICATE')
        ORDER BY source_article_id, pc_related.id, s.score DESC
    """)
    fun findRelatedForArticles(articleIds: Array<Long>): List<RelatedContentRow>
}
