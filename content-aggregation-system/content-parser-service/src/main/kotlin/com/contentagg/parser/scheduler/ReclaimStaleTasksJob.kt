package com.contentagg.parser.scheduler

import com.contentagg.parser.processor.scheduler.ReclaimStaleTasksProcessor
import org.quartz.DisallowConcurrentExecution
import org.quartz.JobExecutionContext
import org.quartz.PersistJobDataAfterExecution
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.scheduling.quartz.QuartzJobBean
import org.springframework.stereotype.Component

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
class ReclaimStaleTasksJob(
    private val reclaimStaleTasksProcessor: ReclaimStaleTasksProcessor,
) : QuartzJobBean() {

    companion object {
        private val log = LoggerFactory.getLogger(ReclaimStaleTasksJob::class.java)
    }

    override fun executeInternal(context: JobExecutionContext) {
        try {
            reclaimStaleTasksProcessor.reclaimStaleTasks()
        } catch (e: Exception) {
            log.error("ReclaimStaleTasksJob failed: {}", e.message, e)
        }
    }
}
