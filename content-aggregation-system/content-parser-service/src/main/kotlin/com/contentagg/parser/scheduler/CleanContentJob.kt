package com.contentagg.parser.scheduler

import com.contentagg.parser.db.service.rawcontent.RawContentCleaningService
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.stereotype.Component

@Component
@DisallowConcurrentExecution
class CleanContentJob(
    private val cleaningService: RawContentCleaningService,
    @Qualifier("cleanerTaskExecutor") private val cleanerTaskExecutor: AsyncTaskExecutor,
    @Value("\${parser.cleaner.batch-size:50}") private val batchSize: Int,
    @Value("\${parser.cleaner.enabled:true}") private val enabled: Boolean,
) : Job {

    companion object {
        private val log = LoggerFactory.getLogger(CleanContentJob::class.java)
    }

    override fun execute(context: JobExecutionContext) {
        if (!enabled) {
            log.debug("CleanContentJob is disabled, skipping")
            return
        }
        cleanerTaskExecutor.execute {
            cleaningService.processBatch(batchSize)
        }
    }
}
