package com.contentagg.parser.db.service.rawcontent

import com.contentagg.parser.db.repository.rawcontent.RawContentRepository
import com.contentagg.parser.db.repository.rawcontent.model.RawContent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Isolated transactional helper for raw_content duplicate handling.
 *
 * Each method runs in its own REQUIRES_NEW transaction so that:
 * - [insertIsolated] — the INSERT failure (unique-constraint violation) is
 *   confined to a fresh transaction that is fully rolled back before control
 *   returns to the caller. The caller's transaction is never touched.
 * - [findExisting] — the lookup after a duplicate INSERT runs in a brand-new
 *   transaction, unaffected by the Postgres aborted-transaction state
 *   (SQLSTATE 25P02) that would poison any reuse of the failed transaction.
 *
 * Both are invoked from the non-transactional [RawContentService.saveRawContent].
 */
@Component
class RawContentDuplicateResolver(
    private val rawContentRepository: RawContentRepository,
) {

    /**
     * Attempt to persist [rawContent] inside an isolated REQUIRES_NEW transaction.
     * Returns the saved entity.
     * If the INSERT violates a UNIQUE constraint the transaction is rolled back and
     * the exception propagates to the caller — it does NOT infect the caller's tx.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insertIsolated(rawContent: RawContent): RawContent =
        rawContentRepository.save(rawContent)

    /**
     * Look up an existing raw_content row in its own REQUIRES_NEW transaction.
     * Must be called only AFTER [insertIsolated] has fully rolled back, so the
     * connection is clean and no aborted-transaction state remains.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun findExisting(sourceType: String, externalId: String): RawContent? =
        rawContentRepository.findBySourceTypeAndExternalId(sourceType, externalId)
}
