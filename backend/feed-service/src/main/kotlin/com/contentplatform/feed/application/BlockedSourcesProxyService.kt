package com.contentplatform.feed.application

import com.contentplatform.feed.application.dto.BlockedSourceItem
import com.contentplatform.feed.application.dto.BlockedSourcesResponse
import com.contentplatform.feed.db.repository.BlockedSourceRepository
import com.contentplatform.feed.infrastructure.cache.FeedCacheService
import com.contentplatform.feed.infrastructure.client.RecSystemClient
import com.contentplatform.feed.infrastructure.client.RecSystemClientException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Owns the user→blocked-source mapping locally.
 *
 * Local feed.blocked_sources is the source of truth — block/unblock is durable even if
 * rec-system is unavailable. rec-system sync is best-effort (swallow errors, log warn),
 * so a future rec-system deploy catches up via the local state on next interaction replay.
 */
@Service
class BlockedSourcesProxyService(
    private val blockedSourceRepository: BlockedSourceRepository,
    private val recSystemClient: RecSystemClient,
    private val feedCacheService: FeedCacheService
) {

    companion object {
        private val log = LoggerFactory.getLogger(BlockedSourcesProxyService::class.java)
    }

    fun block(userId: UUID, sourceId: UUID) {
        blockedSourceRepository.insert(userId, sourceId)
        feedCacheService.deleteFeed(userId.toString())
        log.info("Blocked source (local): userId={} sourceId={}", userId, sourceId)

        try {
            recSystemClient.blockSource(userId, sourceId)
        } catch (e: RecSystemClientException) {
            log.warn("rec-system blockSource sync failed (non-fatal): userId={} sourceId={} error={}", userId, sourceId, e.message)
        }
    }

    fun unblock(userId: UUID, sourceId: UUID) {
        blockedSourceRepository.delete(userId, sourceId)
        feedCacheService.deleteFeed(userId.toString())
        log.info("Unblocked source (local): userId={} sourceId={}", userId, sourceId)

        try {
            recSystemClient.unblockSource(userId, sourceId)
        } catch (e: RecSystemClientException) {
            log.warn("rec-system unblockSource sync failed (non-fatal): userId={} sourceId={} error={}", userId, sourceId, e.message)
        }
    }

    fun list(userId: UUID): BlockedSourcesResponse {
        val rows = blockedSourceRepository.findByUserIdWithMetadata(userId)
        val items = rows.map {
            BlockedSourceItem(
                sourceId = it.sourceId,
                blockedAt = it.blockedAt.toString(),
                sourceType = it.sourceType,
                sourceName = it.sourceName
            )
        }
        return BlockedSourcesResponse(items = items, count = items.size)
    }
}
