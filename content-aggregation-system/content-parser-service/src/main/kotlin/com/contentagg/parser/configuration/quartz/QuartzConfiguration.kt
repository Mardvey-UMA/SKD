package com.contentagg.parser.configuration.quartz

import com.contentagg.parser.configuration.properties.scheduler.SchedulerProperties
import com.contentagg.parser.scheduler.CleanContentJob
import com.contentagg.parser.scheduler.PublishContentJob
import com.contentagg.parser.scheduler.ReclaimStaleTasksJob
import com.contentagg.parser.scheduler.ScheduleSourcesJob
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.SchedulerException
import org.quartz.SimpleScheduleBuilder
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class QuartzConfiguration(
    private val schedulerProperties: SchedulerProperties,
) {

    // -------------------------------------------------------------------------
    // Existing jobs — unchanged (publishContent, cleanContent)
    // -------------------------------------------------------------------------

    @Bean
    fun publishContentJobDetail(): JobDetail =
        JobBuilder.newJob(PublishContentJob::class.java)
            .withIdentity("publishContentJob", "parser")
            .withDescription("Move processed raw_content rows to published_content")
            .storeDurably()
            .build()

    @Bean
    fun publishContentTrigger(publishContentJobDetail: JobDetail): Trigger =
        TriggerBuilder.newTrigger()
            .forJob(publishContentJobDetail)
            .withIdentity("publishContentTrigger", "parser")
            .withSchedule(CronScheduleBuilder.cronSchedule(schedulerProperties.publishCronExpression))
            .build()

    @Bean
    fun cleanContentJobDetail(): JobDetail =
        JobBuilder.newJob(CleanContentJob::class.java)
            .withIdentity("cleanContentJob", "parser")
            .withDescription("Sanitize HTML and extract plain text for raw_content rows")
            .storeDurably()
            .build()

    @Bean
    fun cleanContentTrigger(
        cleanContentJobDetail: JobDetail,
        @Value("\${parser.cleaner.interval-seconds:30}") intervalSeconds: Int,
    ): Trigger =
        TriggerBuilder.newTrigger()
            .forJob(cleanContentJobDetail)
            .withIdentity("cleanContentTrigger", "parser")
            .withSchedule(
                SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInSeconds(intervalSeconds)
                    .repeatForever()
            )
            .build()

    // -------------------------------------------------------------------------
    // New clustered jobs — ScheduleSources, ExecuteTask, ReclaimStaleTasks
    // -------------------------------------------------------------------------

    @Bean
    fun scheduleSourcesJobDetail(): JobDetail =
        JobBuilder.newJob(ScheduleSourcesJob::class.java)
            .withIdentity("scheduleSourcesJob", "parser")
            .withDescription("Leader job: schedule parser tasks for active sources")
            .storeDurably()
            .build()

    @Bean
    fun scheduleSourcesTrigger(scheduleSourcesJobDetail: JobDetail): Trigger =
        TriggerBuilder.newTrigger()
            .forJob(scheduleSourcesJobDetail)
            .withIdentity("scheduleSourcesTrigger", "parser")
            .withSchedule(
                CronScheduleBuilder.cronSchedule(schedulerProperties.cronExpression)
                    .withMisfireHandlingInstructionFireAndProceed()
            )
            .build()

    // ExecuteTaskJob bean/trigger removed from Quartz: in cluster mode (isClustered=true)
    // a fired trigger is acquired by only ONE node per tick, which serialized claims
    // across replicas. ExecuteTaskScheduled (@Scheduled) now runs independently in each
    // replica; SELECT FOR UPDATE SKIP LOCKED inside claimOnePending() remains the sole
    // source of race-safety, which is exactly what we want for parallel claiming.

    @Bean
    fun reclaimStaleTasksJobDetail(): JobDetail =
        JobBuilder.newJob(ReclaimStaleTasksJob::class.java)
            .withIdentity("reclaimStaleTasksJob", "parser")
            .withDescription("Leader job: reset stale RUNNING tasks back to PENDING")
            .storeDurably()
            .build()

    @Bean
    fun reclaimStaleTasksTrigger(reclaimStaleTasksJobDetail: JobDetail): Trigger =
        TriggerBuilder.newTrigger()
            .forJob(reclaimStaleTasksJobDetail)
            .withIdentity("reclaimStaleTasksTrigger", "parser")
            .withSchedule(
                CronScheduleBuilder.cronSchedule(schedulerProperties.reclaimCronExpression)
                    .withMisfireHandlingInstructionFireAndProceed()
            )
            .build()

    // -------------------------------------------------------------------------
    // One-shot legacy cleanup — removes old ParseAllSourcesJob from qrtz_* tables
    // on startup (handles rolling deploys that left the entry in JDBC store)
    // -------------------------------------------------------------------------

    @Bean
    fun parseAllSourcesJobCleanup(scheduler: Scheduler): SmartInitializingSingleton =
        SmartInitializingSingleton {
            val log = LoggerFactory.getLogger(QuartzConfiguration::class.java)
            // Legacy job was registered with group "parser" (matching old QuartzConfiguration identity)
            val legacyJobKey = JobKey.jobKey("parseAllSourcesJob", "parser")
            try {
                if (scheduler.checkExists(legacyJobKey)) {
                    val removed = scheduler.deleteJob(legacyJobKey)
                    log.info(
                        "Legacy ParseAllSourcesJob cleanup: deleteJob({}) returned {}",
                        legacyJobKey,
                        removed
                    )
                }
            } catch (e: SchedulerException) {
                log.warn("Legacy ParseAllSourcesJob cleanup failed: {}", e.message)
            }
        }
}
