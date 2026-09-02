package com.contentplatform.user.unit.job

import com.contentplatform.user.infrastructure.job.CleanupPublishedOutboxJob
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.quartz.JobExecutionContext
import org.springframework.jdbc.core.JdbcTemplate

@Tag("unit")
class CleanupPublishedOutboxJobTest {

    private val jdbcTemplate = mockk<JdbcTemplate>()
    private val job = CleanupPublishedOutboxJob(jdbcTemplate)
    private val jobContext = mockk<JobExecutionContext>(relaxed = true)

    @Test
    fun `execute should call DELETE SQL targeting old published outbox entries`() {
        val sqlSlot = slot<String>()
        every { jdbcTemplate.update(capture(sqlSlot)) } returns 5

        job.execute(jobContext)

        assertThat(sqlSlot.captured).contains("DELETE")
        assertThat(sqlSlot.captured).contains("outbox")
        assertThat(sqlSlot.captured).contains("published_at")
        verify(exactly = 1) { jdbcTemplate.update(any<String>()) }
    }

    @Test
    fun `execute should handle exception gracefully without rethrowing`() {
        every { jdbcTemplate.update(any<String>()) } throws RuntimeException("DB connection lost")

        // Should not throw -- job swallows exceptions and logs them
        job.execute(jobContext)

        verify(exactly = 1) { jdbcTemplate.update(any<String>()) }
    }

    @Test
    fun `execute should work when zero rows deleted`() {
        every { jdbcTemplate.update(any<String>()) } returns 0

        job.execute(jobContext)

        verify(exactly = 1) { jdbcTemplate.update(any<String>()) }
    }
}
