package com.contentagg.aggregator.db.service.content

import com.contentagg.aggregator.db.repository.publishedcontent.PublishedContentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RelatedContentService(
    private val contentRepository: PublishedContentRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(RelatedContentService::class.java)
    }

    fun findRelatedIds(articleIds: List<Long>, limit: Int): Map<Long, List<UUID>> {
        if (articleIds.isEmpty()) return emptyMap()
        log.debug("Finding related ids for {} articles, limit={}", articleIds.size, limit)

        val rows = contentRepository.findRelatedForArticles(articleIds.toTypedArray())

        return rows
            .groupBy { it.sourceArticleId }
            .mapValues { (_, items) ->
                items
                    .sortedByDescending { it.score }
                    .take(limit)
                    .map { it.relatedId }
            }
    }
}
