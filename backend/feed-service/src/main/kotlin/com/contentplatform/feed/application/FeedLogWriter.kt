package com.contentplatform.feed.application

import com.contentplatform.feed.db.repository.FeedItemRepository
import com.contentplatform.feed.db.repository.FeedRequestRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Transactional inner component that performs the actual DB write for feed request logging.
 * Separated from FeedLogServiceImpl so that @Transactional is honoured through Spring AOP proxy
 * when called from an async lambda in FeedLogServiceImpl.
 */
@Component
class FeedLogWriter(
    private val feedRequestRepository: FeedRequestRepository,
    private val feedItemRepository: FeedItemRepository,
    private val feedLoggingMapper: FeedLoggingMapper
) {

    companion object {
        private val log = LoggerFactory.getLogger(FeedLogWriter::class.java)
    }

    @Transactional
    fun write(request: FeedRequestLog, items: List<FeedItemLog>) {
        val requestEntity = feedLoggingMapper.toEntity(request)
        feedRequestRepository.save(requestEntity)
        val itemEntities = items.map { feedLoggingMapper.toEntity(it) }
        feedItemRepository.saveAll(itemEntities)
        log.debug("Logged feed request requestId={} with {} items", request.requestId, items.size)
    }
}
