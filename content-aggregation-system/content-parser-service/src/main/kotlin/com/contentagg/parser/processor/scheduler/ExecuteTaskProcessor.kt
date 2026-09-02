package com.contentagg.parser.processor.scheduler

import com.contentagg.parser.configuration.properties.scheduler.SchedulerProperties
import com.contentagg.parser.db.service.parsertask.ParserTaskService
import com.contentagg.parser.integration.rest.configservice.cache.ParserConfigService
import com.contentagg.parser.processor.parser.ContentParser
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component

/**
 * Processor for the worker-pool ExecuteTaskJob.
 * Claims one PENDING task via FOR UPDATE SKIP LOCKED, dispatches to the matching
 * ContentParser outside any transaction, then writes the outcome via a separate
 * REQUIRES_NEW transaction.
 *
 * No @Transactional here — all persistence hops delegate to ParserTaskService
 * methods which are each @Transactional(REQUIRES_NEW).
 */
@Component
class ExecuteTaskProcessor(
    private val parserTaskService: ParserTaskService,
    private val parserConfigService: ParserConfigService,
    private val contentParsers: List<ContentParser>,
    private val schedulerProperties: SchedulerProperties,
) {
    companion object {
        private val log = LoggerFactory.getLogger(ExecuteTaskProcessor::class.java)
        private const val MDC_TASK_ID = "taskId"
        private const val MDC_SOURCE_ID = "sourceId"
        private const val MDC_SOURCE_TYPE = "sourceType"
        private const val MDC_INSTANCE_ID = "instanceId"
    }

    /**
     * Claim and execute one task. Returns true if a task was processed (regardless of success).
     * MDC keys taskId, sourceId, sourceType, instanceId are set during processing and cleared
     * in finally.
     */
    fun executeOneClaim(instanceId: String): Boolean {
        val enabledTypes = schedulerProperties.enabledSourceTypes.map { it.uppercase() }
        if (enabledTypes.isEmpty()) {
            log.debug("enabledSourceTypes is empty; skipping claim")
            return false
        }

        val claimed = parserTaskService.claimOnePending(enabledTypes, instanceId) ?: run {
            log.debug("no pending task")
            return false
        }

        try {
            MDC.put(MDC_TASK_ID, claimed.id.toString())
            MDC.put(MDC_SOURCE_ID, claimed.sourceId.toString())
            MDC.put(MDC_SOURCE_TYPE, claimed.sourceType)
            MDC.put(MDC_INSTANCE_ID, instanceId)
            processClaimed(claimed)
        } finally {
            MDC.remove(MDC_TASK_ID)
            MDC.remove(MDC_SOURCE_ID)
            MDC.remove(MDC_SOURCE_TYPE)
            MDC.remove(MDC_INSTANCE_ID)
        }
        return true
    }

    private fun processClaimed(claimed: ParserTaskService.ClaimedTask) {
        val sourceConfig = parserConfigService.getSourceById(claimed.sourceId)
        if (sourceConfig == null) {
            log.warn(
                "Source config not found for sourceId={}; requeueOrFail taskId={}",
                claimed.sourceId, claimed.id,
            )
            parserTaskService.requeueOrFail(
                taskId = claimed.id,
                errorMessage = "source config not found",
                currentRetryCount = claimed.retryCount,
                maxRetryCount = schedulerProperties.maxRetryCount,
            )
            return
        }

        val parser = findParser(claimed.sourceType)
        if (parser == null) {
            log.warn(
                "No ContentParser supports sourceType={}; requeueOrFail taskId={}",
                claimed.sourceType, claimed.id,
            )
            parserTaskService.requeueOrFail(
                taskId = claimed.id,
                errorMessage = "no parser for sourceType=${claimed.sourceType}",
                currentRetryCount = claimed.retryCount,
                maxRetryCount = schedulerProperties.maxRetryCount,
            )
            return
        }

        try {
            val saved = parser.parse(sourceConfig)
            log.info(
                "ExecuteTaskJob: taskId={} sourceType={} saved={}",
                claimed.id, claimed.sourceType, saved,
            )
            parserTaskService.markCompletedAndClearClaim(claimed.id)
        } catch (e: Exception) {
            log.warn("ExecuteTaskJob: taskId={} failed: {}", claimed.id, e.message, e)
            parserTaskService.requeueOrFail(
                taskId = claimed.id,
                errorMessage = e.message,
                currentRetryCount = claimed.retryCount,
                maxRetryCount = schedulerProperties.maxRetryCount,
            )
        }
    }

    private fun findParser(sourceType: String): ContentParser? =
        contentParsers.firstOrNull { it.supports(sourceType) }
}
