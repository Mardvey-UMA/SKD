package com.skd.subscription.unit

import com.skd.subscription.db.repository.webhook.WebhookLogRepository
import com.skd.subscription.job.CleanupOldWebhookLogJob
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
class CleanupOldWebhookLogJobTest {

    private val webhookLogRepository = mockk<WebhookLogRepository>()

    private val job = CleanupOldWebhookLogJob(
        webhookLogRepository = webhookLogRepository
    )

    private val jobContext = mockk<JobExecutionContext>(relaxed = true)

    @Test
    fun `should delete webhook log entries older than 30 days`() {
        val cutoffSlot = slot<Instant>()
        every { webhookLogRepository.deleteReceivedBefore(capture(cutoffSlot)) } returns 10

        job.execute(jobContext)

        val cutoff = cutoffSlot.captured
        val expectedCutoff = Instant.now().minus(Duration.ofDays(30))
        // Allow 5 seconds tolerance for test execution time
        assertThat(cutoff).isBetween(
            expectedCutoff.minusSeconds(5),
            expectedCutoff.plusSeconds(5)
        )
        verify(exactly = 1) { webhookLogRepository.deleteReceivedBefore(any()) }
    }

    @Test
    fun `should handle case when no entries to delete`() {
        every { webhookLogRepository.deleteReceivedBefore(any()) } returns 0

        job.execute(jobContext)

        verify(exactly = 1) { webhookLogRepository.deleteReceivedBefore(any()) }
    }
}
