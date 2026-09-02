package com.contentagg.parser.processor.scheduler

import com.contentagg.parser.configuration.properties.scheduler.SchedulerProperties
import com.contentagg.parser.db.service.parsertask.ParserTaskService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ReclaimStaleTasksProcessor(
    private val parserTaskService: ParserTaskService,
    private val schedulerProperties: SchedulerProperties,
) {
    companion object {
        private val log = LoggerFactory.getLogger(ReclaimStaleTasksProcessor::class.java)
    }

    fun reclaimStaleTasks() {
        val threshold = schedulerProperties.staleTaskThresholdMinutes
        val reset = parserTaskService.resetStale(threshold)
        if (reset > 0) {
            log.info("ReclaimStaleTasksJob: reset {} stale tasks (threshold={} min)", reset, threshold)
        } else {
            log.debug("ReclaimStaleTasksJob: no stale tasks (threshold={} min)", threshold)
        }
    }
}
