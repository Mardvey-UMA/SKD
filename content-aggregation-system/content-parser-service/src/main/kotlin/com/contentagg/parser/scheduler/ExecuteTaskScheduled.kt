package com.contentagg.parser.scheduler

import com.contentagg.parser.configuration.properties.scheduler.SchedulerProperties
import com.contentagg.parser.processor.scheduler.ExecuteTaskProcessor
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Worker-pool scheduled task: fires every N seconds in EVERY replica.
 *
 * Replaces the old Quartz ExecuteTaskJob. In Quartz clustered mode (isClustered=true)
 * a fired trigger is acquired by exactly one node per tick, which serialized claims
 * across replicas and defeated the whole point of horizontal scaling. Spring @Scheduled
 * is process-local: each replica runs its own loop, and race-safety comes from
 * SELECT FOR UPDATE SKIP LOCKED inside ExecuteTaskProcessor.claimOnePending().
 *
 * Leader-only jobs (ScheduleSourcesJob, ReclaimStaleTasksJob) stay on Quartz cluster.
 */
@Component
class ExecuteTaskScheduled(
    private val executeTaskProcessor: ExecuteTaskProcessor,
    private val schedulerProperties: SchedulerProperties,
) {

    companion object {
        private val log = LoggerFactory.getLogger(ExecuteTaskScheduled::class.java)
    }

    private lateinit var instanceId: String

    @PostConstruct
    fun init() {
        instanceId = System.getenv("HOSTNAME") ?: "unknown"
        log.info(
            "ExecuteTaskScheduled bound to instanceId={} executeIntervalSeconds={}",
            instanceId,
            schedulerProperties.executeIntervalSeconds,
        )
    }

    @Scheduled(fixedDelayString = "#{\${parser.scheduler.execute-interval-seconds:5} * 1000}")
    fun execute() {
        try {
            executeTaskProcessor.executeOneClaim(instanceId)
        } catch (e: Exception) {
            log.error("ExecuteTaskScheduled unexpected failure: {}", e.message, e)
        }
    }
}
