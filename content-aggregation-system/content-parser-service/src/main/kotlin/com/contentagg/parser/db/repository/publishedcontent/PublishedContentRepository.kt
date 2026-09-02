package com.contentagg.parser.db.repository.publishedcontent

import com.contentagg.parser.db.repository.publishedcontent.model.PublishedContent
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
interface PublishedContentRepository : CrudRepository<PublishedContent, UUID> {

    @Modifying
    @Query(
        """
        INSERT INTO published_content (
            content_id, external_id, title, description, content, content_format,
            source_id, source_type, source_subtype, url, published_at,
            author_id, author_name, media, metadata,
            dedup_article_id, content_hash,
            content_html, content_text, preview_text, content_text_length
        ) VALUES (
            :contentId, :externalId, :title, :description, :content, :contentFormat,
            :sourceId, :sourceType, :sourceSubtype, :url, :publishedAt,
            :authorId, :authorName, :media::jsonb, :metadata::jsonb,
            :dedupArticleId, :contentHash,
            :contentHtml, :contentText, :previewText, :contentTextLength
        ) ON CONFLICT (source_type, external_id) DO NOTHING
        """
    )
    fun insertOnConflictDoNothing(
        contentId: UUID,
        externalId: String,
        title: String?,
        description: String?,
        content: String?,
        contentFormat: String,
        sourceId: UUID,
        sourceType: String,
        sourceSubtype: String?,
        url: String?,
        publishedAt: OffsetDateTime?,
        authorId: String?,
        authorName: String?,
        media: String?,
        metadata: String?,
        dedupArticleId: Long?,
        contentHash: String?,
        contentHtml: String?,
        contentText: String?,
        previewText: String?,
        contentTextLength: Int?,
    )
}
