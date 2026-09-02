package com.skd.userinteractions.job

import com.skd.userinteractions.configuration.InteractionsProperties
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.YearMonth

@Component
class DropOldPartitionsJob(
    private val jdbcTemplate: JdbcTemplate,
    private val properties: InteractionsProperties
) : Job {

    companion object {
        private val log = LoggerFactory.getLogger(DropOldPartitionsJob::class.java)
        private val PARTITION_NAME_REGEX = Regex("user_interactions_(\\d{4})_(\\d{2})")
    }

    override fun execute(context: JobExecutionContext) {
        val cutoff = YearMonth.now().minusMonths(properties.partition.retentionMonths.toLong())

        val tableNames = jdbcTemplate.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'interactions' AND tablename LIKE 'user_interactions_2%' AND tablename != 'user_interactions_default'",
            String::class.java
        )

        for (tableName in tableNames) {
            if (tableName == "user_interactions_default") continue

            val match = PARTITION_NAME_REGEX.matchEntire(tableName) ?: continue
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val partitionMonth = YearMonth.of(year, month)

            if (partitionMonth.isBefore(cutoff)) {
                log.info("Dropping old partition: {}", tableName)
                jdbcTemplate.execute("DROP TABLE IF EXISTS $tableName")
                log.info("Dropped partition: {}", tableName)
            }
        }
    }
}
