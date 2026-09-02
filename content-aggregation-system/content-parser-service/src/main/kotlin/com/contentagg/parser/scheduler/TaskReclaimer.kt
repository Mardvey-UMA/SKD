package com.contentagg.parser.scheduler

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.util.UUID
import org.quartz.Scheduler
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Captures the Quartz scheduler's cluster-unique instanceId once at @PostConstruct and reuses it:
 *   - as the `claimed_by` value written by ExecuteTaskJob (see ExecuteTaskProcessor.executeOneClaim)
 *   - as the filter key in the @PreDestroy hook that resets THIS replica's own RUNNING tasks to PENDING
 *
 * instanceId choice: `scheduler.schedulerInstanceId`. With org.quartz.scheduler.instanceId=AUTO this yields
 * a cluster-unique value stamped by Quartz in `qrtz_fired_triggers.instance_name` + `qrtz_scheduler_state.instance_name`.
 * Reusing the same value for `claimed_by` means operators can correlate Quartz cluster checkin rows with
 * parser_tasks.claimed_by in logs and dashboards.
 *
 * Fallback: `HOSTNAME` env or random UUID — only if Scheduler.getSchedulerInstanceId() throws at @PostConstruct.
 */
@Component
class TaskReclaimer(
    private val jdbcTemplate: JdbcTemplate,
    private val scheduler: Scheduler,
) {
    companion object {
        private val log = LoggerFactory.getLogger(TaskReclaimer::class.java)
        private const val RECLAIM_SQL = """
            UPDATE data_flow.parser_tasks
            SET status='PENDING',
                claimed_by=NULL,
                claimed_at=NULL,
                started_at=NULL,
                updated_at=NOW()
            WHERE claimed_by = ? AND status = 'RUNNING'
        """
    }

    private lateinit var instanceId: String

    @PostConstruct
    fun init() {
        instanceId = try {
            scheduler.schedulerInstanceId
        } catch (e: Exception) {
            val fallback = System.getenv("HOSTNAME") ?: "unknown-${UUID.randomUUID()}"
            log.warn("Falling back to HOSTNAME for instanceId={} (cause: {})", fallback, e.message)
            fallback
        }
        log.info("TaskReclaimer bound to instanceId={}", instanceId)
    }

    @PreDestroy
    fun reclaimOwnTasks() {
        if (!::instanceId.isInitialized) {
            log.info("TaskReclaimer @PreDestroy: instanceId not initialized, skipping")
            return
        }
        val rows = try {
            jdbcTemplate.update(RECLAIM_SQL, instanceId)
        } catch (e: Exception) {
            log.error("TaskReclaimer @PreDestroy failed: {}", e.message, e)
            return
        }
        log.info("TaskReclaimer @PreDestroy: reset {} own RUNNING tasks (instanceId={})", rows, instanceId)
    }
}
