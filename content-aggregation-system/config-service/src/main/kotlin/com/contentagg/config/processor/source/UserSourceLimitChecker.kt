package com.contentagg.config.processor.source

import com.contentagg.config.configuration.properties.SourceLimitProperties
import com.contentagg.config.exception.SourceLimitExceededException
import com.contentagg.config.integration.rest.feed.SourceAdditionsCountClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Reusable "check per-user source addition limit" helper.
 * Used by every Create*SourceProcessor (Phase 2).
 *
 * Behaviour:
 *   - disabled → no-op
 *   - userId null/blank → no-op (internal callers without user context)
 *   - feed-service unreachable after retries → warn + proceed (fail-open)
 *   - count >= limit → SourceLimitExceededException (→ 429)
 */
@Component
class UserSourceLimitChecker(
    private val additionsCountClient: SourceAdditionsCountClient,
    private val sourceLimitProperties: SourceLimitProperties,
) {

    companion object {
        private val log = LoggerFactory.getLogger(UserSourceLimitChecker::class.java)
    }

    fun ensureUnderLimit(userId: String?) {
        if (!sourceLimitProperties.enabled) {
            log.debug("Limit check disabled (feature flag)")
            return
        }
        if (userId.isNullOrBlank()) {
            log.debug("No userId in request — skipping limit check")
            return
        }
        val response = additionsCountClient.tryGetCount(userId)
        if (response == null) {
            log.warn("Limit check failed-open for userId={}", userId)
            return
        }
        val configuredLimit = sourceLimitProperties.perUser
        val effectiveLimit = if (response.limit > 0) response.limit else configuredLimit
        if (response.count >= effectiveLimit) {
            log.info("Source limit reached for userId={} count={} limit={}", userId, response.count, effectiveLimit)
            throw SourceLimitExceededException(current = response.count, limit = effectiveLimit)
        }
    }
}
