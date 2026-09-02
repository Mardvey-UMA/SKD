package com.skd.userinteractions.unit

import com.skd.userinteractions.configuration.InteractionsProperties
import com.skd.userinteractions.job.CreateNextPartitionJob
import com.skd.userinteractions.job.DropOldPartitionsJob
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.quartz.JobExecutionContext
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import java.time.YearMonth

@Tag("unit")
class PartitionJobsTest {

    private val jdbcTemplate = mockk<JdbcTemplate>(relaxed = true)
    private lateinit var properties: InteractionsProperties

    @BeforeEach
    fun setUp() {
        properties = InteractionsProperties().apply {
            partition = InteractionsProperties.Partition().apply {
                retentionMonths = 6
            }
        }
    }

    @Nested
    inner class `CreateNextPartitionJob tests` {

        private lateinit var job: CreateNextPartitionJob

        @BeforeEach
        fun setUp() {
            job = CreateNextPartitionJob(jdbcTemplate)
        }

        @Test
        fun `creates partition for 2 months ahead`() {
            val context = mockk<JobExecutionContext>(relaxed = true)
            val sqlSlot = slot<String>()

            every { jdbcTemplate.execute(capture(sqlSlot)) } returns Unit

            job.execute(context)

            // Should create a partition table named with the target month
            val sql = sqlSlot.captured
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS")
            assertThat(sql).contains("user_interactions_")
            assertThat(sql).contains("PARTITION OF user_interactions")
            assertThat(sql).contains("FOR VALUES FROM")
        }

        @Test
        fun `partition name follows naming convention user_interactions_YYYY_MM`() {
            val context = mockk<JobExecutionContext>(relaxed = true)
            val sqlSlot = slot<String>()

            every { jdbcTemplate.execute(capture(sqlSlot)) } returns Unit

            job.execute(context)

            val targetMonth = YearMonth.now().plusMonths(2)
            val expectedName = "user_interactions_${targetMonth.year}_${String.format("%02d", targetMonth.monthValue)}"
            assertThat(sqlSlot.captured).contains(expectedName)
        }
    }

    @Nested
    inner class `DropOldPartitionsJob tests` {

        private lateinit var job: DropOldPartitionsJob

        @BeforeEach
        fun setUp() {
            job = DropOldPartitionsJob(jdbcTemplate, properties)
        }

        @Test
        fun `drops partitions older than retention months`() {
            val context = mockk<JobExecutionContext>(relaxed = true)

            // Mock query to return old partition names
            val oldMonth = YearMonth.now().minusMonths(7)
            val oldPartitionName = "user_interactions_${oldMonth.year}_${String.format("%02d", oldMonth.monthValue)}"
            every {
                jdbcTemplate.queryForList(any<String>(), eq(String::class.java))
            } returns listOf(oldPartitionName)

            job.execute(context)

            verify { jdbcTemplate.execute(match<String> { it.contains("DROP TABLE IF EXISTS") && it.contains(oldPartitionName) }) }
        }

        @Test
        fun `does not drop recent partitions`() {
            val context = mockk<JobExecutionContext>(relaxed = true)

            // No old partitions found
            every {
                jdbcTemplate.queryForList(any<String>(), eq(String::class.java))
            } returns emptyList()

            job.execute(context)

            // Should not execute any DROP statements
            verify(exactly = 0) { jdbcTemplate.execute(match<String> { it.contains("DROP TABLE") }) }
        }

        @Test
        fun `does not drop default partition`() {
            val context = mockk<JobExecutionContext>(relaxed = true)

            every {
                jdbcTemplate.queryForList(any<String>(), eq(String::class.java))
            } returns listOf("user_interactions_default")

            job.execute(context)

            verify(exactly = 0) { jdbcTemplate.execute(match<String> { it.contains("DROP TABLE") && it.contains("default") }) }
        }
    }
}
