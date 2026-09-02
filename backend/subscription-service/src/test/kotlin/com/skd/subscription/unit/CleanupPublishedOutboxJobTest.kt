package com.skd.subscription.unit

import com.skd.subscription.db.repository.outbox.OutboxRepository
import com.skd.subscription.job.CleanupPublishedOutboxJob
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.quartz.JobExecutionContext
import java.time.Duration
import java.time.Instant

@Tag("unit")
class CleanupPublishedOutboxJobTest {

    private val outboxRepository = mockk<OutboxRepository>()

    private val job = CleanupPublishedOutboxJob(
        outboxRepository = outboxRepository
    )

    private val jobContext = mockk<JobExecutionContext>(relaxed = true)

    @Test
    fun `should delete published outbox entries older than 3 days`() {
        val cutoffSlot = slot<Instant>()
        every { outboxRepository.deletePublishedBefore(capture(cutoffSlot)) } returns 5

        job.execute(jobContext)

        val cutoff = cutoffSlot.captured
        val expectedCutoff = Instant.now().minus(Duration.ofDays(3))
        // Allow 5 seconds tolerance for test execution time
        assertThat(cutoff).isBetween(
            expectedCutoff.minusSeconds(5),
            expectedCutoff.plusSeconds(5)
        )
        verify(exactly = 1) { outboxRepository.deletePublishedBefore(any()) }
    }

    @Test
    fun `should handle case when no entries to delete`() {
        every { outboxRepository.deletePublishedBefore(any()) } returns 0

        job.execute(jobContext)

        verify(exactly = 1) { outboxRepository.deletePublishedBefore(any()) }
    }
}
