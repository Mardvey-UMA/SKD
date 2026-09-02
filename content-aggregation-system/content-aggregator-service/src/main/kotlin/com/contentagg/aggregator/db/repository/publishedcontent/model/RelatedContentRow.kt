package com.contentagg.aggregator.db.repository.publishedcontent.model

import org.springframework.data.relational.core.mapping.Column
import java.util.UUID

data class RelatedContentRow(
    @Column("source_article_id")
    val sourceArticleId: Long,

    @Column("related_id")
    val relatedId: UUID,

    @Column("score")
    val score: Float
)
