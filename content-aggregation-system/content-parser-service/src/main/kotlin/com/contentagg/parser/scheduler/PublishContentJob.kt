package com.contentagg.parser.scheduler

import com.contentagg.parser.configuration.properties.scheduler.SchedulerProperties
import com.contentagg.parser.processor.publisher.PublishContentProcessor
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Scope
import org.springframework.scheduling.quartz.QuartzJobBean
import org.springframework.stereotype.Component

@Component
@Scope("prototype")
class PublishContentJob(
    private val publishContentProcessor: PublishContentProcessor,
    private val schedulerProperties: SchedulerProperties,
) : QuartzJobBean() {

    companion object {
        private val log = LoggerFactory.getLogger(PublishContentJob::class.java)
    }

    override fun executeInternal(context: JobExecutionContext) {
        log.info("Quartz job triggered: PublishContentJob")
        try {
            val published = publishContentProcessor.publishBatch(schedulerProperties.publishBatchSize)
            if (published > 0) {
                log.info("PublishContentJob: published {} rows", published)
            }
        } catch (e: Exception) {
            log.error("PublishContentJob execution failed: {}", e.message, e)
        }
    }
}
