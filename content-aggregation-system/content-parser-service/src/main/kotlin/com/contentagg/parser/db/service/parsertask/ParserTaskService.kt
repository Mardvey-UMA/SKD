package com.contentagg.parser.db.service.parsertask

import com.contentagg.parser.db.repository.parsertask.ParserTaskRepository
import com.contentagg.parser.db.repository.parsertask.model.ParserTask
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * Service layer wrapping ParserTaskRepository.
 * Controls transaction boundaries for parser task persistence.
 *
 * Low-level SQL (FOR UPDATE SKIP LOCKED, INSERT ON CONFLICT, conditional UPDATE)
 * is executed via NamedParameterJdbcTemplate, consistent with PublishContentProcessor.
 */
@Service
class ParserTaskService(
    private val parserTaskRepository: ParserTaskRepository,
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) {
    companion object {
        private val log = LoggerFactory.getLogger(ParserTaskService::class.java)

        private const val INSERT_PENDING_SQL = """
            INSERT INTO data_flow.parser_tasks
                (id, source_id, source_type, task_type, status, scheduled_at,
                 retry_count, created_at, updated_at)
            VALUES
                (gen_random_uuid(), :sourceId, :sourceType, :taskType, 'PENDING',
                 NOW(), 0, NOW(), NOW())
            ON CONFLICT (source_id) WHERE status IN ('PENDING','RUNNING') DO NOTHING
        """

        private const val CLAIM_SELECT_SQL = """
            SELECT id, source_id, source_type, retry_count
            FROM data_flow.parser_tasks
            WHERE status = 'PENDING'
              AND source_type = ANY (CAST(:enabledTypes AS text[]))
            ORDER BY created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
        """

        private const val CLAIM_UPDATE_SQL = """
            UPDATE data_flow.parser_tasks
            SET status='RUNNING',
                claimed_by=:instanceId,
                claimed_at=NOW(),
                started_at=NOW(),
                updated_at=NOW()
            WHERE id = :taskId
        """

        private const val COMPLETE_SQL = """
            UPDATE data_flow.parser_tasks
            SET status='COMPLETED',
                completed_at=NOW(),
                claimed_by=NULL,
                claimed_at=NULL,
                updated_at=NOW()
            WHERE id=:taskId
        """

        private const val REQUEUE_SQL = """
            UPDATE data_flow.parser_tasks
            SET status='PENDING',
                retry_count = COALESCE(retry_count, 0) + 1,
                error_message=:msg,
                claimed_by=NULL,
                claimed_at=NULL,
                started_at=NULL,
                updated_at=NOW()
            WHERE id=:taskId
        """

        private const val FAIL_FINAL_SQL = """
            UPDATE data_flow.parser_tasks
            SET status='FAILED',
                retry_count = COALESCE(retry_count, 0) + 1,
                error_message=:msg,
                completed_at=NOW(),
                claimed_by=NULL,
                claimed_at=NULL,
                updated_at=NOW()
            WHERE id=:taskId
        """

        private const val RESET_STALE_SQL = """
            UPDATE data_flow.parser_tasks
            SET status='PENDING',
                claimed_by=NULL,
                claimed_at=NULL,
                started_at=NULL,
                updated_at=NOW()
            WHERE status='RUNNING'
              AND claimed_at < :threshold
        """

        private const val RESET_OWN_RUNNING_SQL = """
            UPDATE data_flow.parser_tasks
            SET status='PENDING',
                claimed_by=NULL,
                claimed_at=NULL,
                updated_at=NOW()
            WHERE claimed_by=:instanceId
              AND status='RUNNING'
        """
    }

    /**
     * Data class returned when a task is successfully claimed.
     */
    data class ClaimedTask(
        val id: UUID,
        val sourceId: UUID,
        val sourceType: String,
        val retryCount: Int,
    )

    // -------------------------------------------------------------------------
    // Existing methods — kept unchanged for backward compatibility with
    // ParseAllSourcesProcessor (removed in subtask 12) and other callers.
    // -------------------------------------------------------------------------

    /**
     * Create and save a new parser task with RUNNING status.
     */
    @Transactional
    fun createRunningTask(sourceId: UUID, sourceType: String): ParserTask {
        val task = ParserTask.newInstance(
            sourceId = sourceId,
            sourceType = sourceType,
            taskType = "PARSE",
            status = "RUNNING",
            scheduledAt = LocalDateTime.now(),
            startedAt = LocalDateTime.now(),
        )
        val saved = parserTaskRepository.save(task)
        log.debug("Created parser task: id={}, sourceId={}", saved.id, sourceId)
        return saved
    }

    /**
     * Mark task as completed.
     */
    @Transactional
    fun markCompleted(task: ParserTask) {
        val completed = task.withCompleted(LocalDateTime.now())
        parserTaskRepository.save(completed)
        log.debug("Marked task {} as COMPLETED", task.id)
    }

    /**
     * Mark task as failed with error message.
     */
    @Transactional
    fun markFailed(task: ParserTask, errorMessage: String) {
        val failed = task.withFailed(errorMessage)
        parserTaskRepository.save(failed)
        log.debug("Marked task {} as FAILED: {}", task.id, errorMessage)
    }

    /**
     * Save a task (any state).
     */
    @Transactional
    fun save(task: ParserTask): ParserTask = parserTaskRepository.save(task)

    /**
     * Find task by ID.
     */
    fun findById(id: UUID): ParserTask? = parserTaskRepository.findById(id).orElse(null)

    /**
     * Find pending tasks scheduled before given time.
     */
    fun findPendingTasksBefore(scheduledAt: LocalDateTime): List<ParserTask> =
        parserTaskRepository.findByStatusAndScheduledAtLessThanEqual("PENDING", scheduledAt)

    /**
     * Count all tasks.
     */
    fun count(): Long = parserTaskRepository.count()

    // -------------------------------------------------------------------------
    // New claim / requeue / reset operations for horizontal-scaling job workers.
    // All use REQUIRES_NEW so they can be called from inside or outside a Tx
    // without marking the outer transaction rollback-only on constraint violations.
    // -------------------------------------------------------------------------

    /**
     * Insert a PENDING task for the given source if no active (PENDING or RUNNING)
     * task already exists for that source. Uses ON CONFLICT on the partial unique
     * index uq_parser_tasks_active_source so duplicate inserts are silently ignored.
     *
     * Returns true if a row was inserted, false if a conflict was detected.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insertPendingIfAbsent(sourceId: UUID, sourceType: String, taskType: String = "PARSE_SOURCE"): Boolean {
        val params = MapSqlParameterSource()
            .addValue("sourceId", sourceId)
            .addValue("sourceType", sourceType)
            .addValue("taskType", taskType)
        val rows = namedParameterJdbcTemplate.update(INSERT_PENDING_SQL, params)
        log.debug("insertPendingIfAbsent sourceId={} sourceType={} inserted={}", sourceId, sourceType, rows == 1)
        return rows == 1
    }

    /**
     * Claim one PENDING task from the given enabled source types.
     * Uses SELECT … FOR UPDATE SKIP LOCKED to avoid contention between
     * concurrent worker instances. Both SELECT and UPDATE run in the same
     * REQUIRES_NEW transaction.
     *
     * Returns the claimed task details, or null if no eligible task exists.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun claimOnePending(enabledTypes: List<String>, instanceId: String): ClaimedTask? {
        if (enabledTypes.isEmpty()) return null

        val selectParams = MapSqlParameterSource()
            .addValue("enabledTypes", enabledTypes.toTypedArray())

        val row = namedParameterJdbcTemplate.query(CLAIM_SELECT_SQL, selectParams) { rs, _ ->
            ClaimedTask(
                id = rs.getObject("id", UUID::class.java),
                sourceId = rs.getObject("source_id", UUID::class.java),
                sourceType = rs.getString("source_type"),
                retryCount = rs.getInt("retry_count"),
            )
        }.firstOrNull() ?: return null

        val updateParams = MapSqlParameterSource()
            .addValue("instanceId", instanceId)
            .addValue("taskId", row.id)
        namedParameterJdbcTemplate.update(CLAIM_UPDATE_SQL, updateParams)

        log.debug("Claimed task id={} sourceType={} by instanceId={}", row.id, row.sourceType, instanceId)
        return row
    }

    /**
     * Mark a task as COMPLETED and clear claim fields.
     * Uses REQUIRES_NEW so a failure here does not roll back the caller's Tx.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markCompletedAndClearClaim(taskId: UUID) {
        namedParameterJdbcTemplate.update(
            COMPLETE_SQL,
            MapSqlParameterSource("taskId", taskId),
        )
        log.debug("markCompletedAndClearClaim taskId={}", taskId)
    }

    /**
     * Requeue or permanently fail a task based on retry count.
     *
     * If currentRetryCount + 1 < maxRetryCount → status=PENDING, retry_count++
     * Otherwise → status=FAILED, retry_count++
     *
     * errorMessage is truncated to 2000 chars before storage.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun requeueOrFail(taskId: UUID, errorMessage: String?, currentRetryCount: Int, maxRetryCount: Int) {
        val truncatedMsg = errorMessage?.take(2000)
        val params = MapSqlParameterSource()
            .addValue("taskId", taskId)
            .addValue("msg", truncatedMsg)
        val sql = if (currentRetryCount + 1 < maxRetryCount) REQUEUE_SQL else FAIL_FINAL_SQL
        namedParameterJdbcTemplate.update(sql, params)
        log.debug(
            "requeueOrFail taskId={} retryCount={} maxRetry={} action={}",
            taskId,
            currentRetryCount,
            maxRetryCount,
            if (currentRetryCount + 1 < maxRetryCount) "REQUEUE" else "FAIL",
        )
    }

    /**
     * Reset stale RUNNING tasks whose claimed_at is older than the given threshold.
     * Returns the number of rows updated.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun resetStale(thresholdMinutes: Int): Int {
        val threshold = LocalDateTime.now().minusMinutes(thresholdMinutes.toLong())
        val rows = namedParameterJdbcTemplate.update(
            RESET_STALE_SQL,
            MapSqlParameterSource("threshold", threshold),
        )
        if (rows > 0) {
            log.warn("Reclaimed {} stale RUNNING tasks (threshold={} min)", rows, thresholdMinutes)
        }
        return rows
    }

    /**
     * Reset all RUNNING tasks claimed by a specific instance back to PENDING.
     * Used by TaskReclaimer @PreDestroy for graceful shutdown.
     * Returns the number of rows updated.
     */
    @Transactional
    fun resetOwnRunningTasks(instanceId: String): Int {
        val rows = namedParameterJdbcTemplate.update(
            RESET_OWN_RUNNING_SQL,
            MapSqlParameterSource("instanceId", instanceId),
        )
        if (rows > 0) {
            log.info("Released {} RUNNING tasks claimed by instanceId={} on shutdown", rows, instanceId)
        }
        return rows
    }
}
