package com.skd.userinteractions.job

import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.YearMonth

@Component
class CreateNextPartitionJob(
    private val jdbcTemplate: JdbcTemplate
) : Job {

    companion object {
        private val log = LoggerFactory.getLogger(CreateNextPartitionJob::class.java)
    }

    override fun execute(context: JobExecutionContext) {
        val targetMonth = YearMonth.now().plusMonths(2)
        val partitionName = "user_interactions_${targetMonth.year}_${String.format("%02d", targetMonth.monthValue)}"
        val fromDate = targetMonth.atDay(1)
        val toDate = targetMonth.plusMonths(1).atDay(1)

        val sql = """
            CREATE TABLE IF NOT EXISTS $partitionName
            PARTITION OF user_interactions
            FOR VALUES FROM ('$fromDate') TO ('$toDate')
        """.trimIndent()

        log.info("Creating partition: {}", partitionName)
        jdbcTemplate.execute(sql)
        log.info("Partition created (or already exists): {}", partitionName)
    }
}
