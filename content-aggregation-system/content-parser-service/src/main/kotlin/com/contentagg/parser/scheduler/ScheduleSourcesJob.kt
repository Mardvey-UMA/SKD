package com.contentagg.parser.scheduler

import com.contentagg.parser.processor.scheduler.ScheduleSourcesProcessor
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
class ScheduleSourcesJob(
    private val scheduleSourcesProcessor: ScheduleSourcesProcessor,
) : QuartzJobBean() {

    companion object {
        private val log = LoggerFactory.getLogger(ScheduleSourcesJob::class.java)
    }

    override fun executeInternal(context: JobExecutionContext) {
        try {
            scheduleSourcesProcessor.scheduleActiveSources()
        } catch (e: Exception) {
            log.error("ScheduleSourcesJob failed: {}", e.message, e)
        }
    }
}
