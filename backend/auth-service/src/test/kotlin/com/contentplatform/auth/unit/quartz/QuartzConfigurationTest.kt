package com.contentplatform.auth.unit.quartz

import com.contentplatform.auth.quartz.QuartzConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.quartz.CronTrigger
import org.quartz.JobDetail

@Tag("unit")
class QuartzConfigurationTest {

    private val config = QuartzConfiguration()

    @Test
    fun `should create verification tokens cleanup job detail`() {
        val jobDetail: JobDetail = config.cleanupExpiredVerificationTokensJobDetail()

        assertThat(jobDetail.key.name).contains("verification")
        assertThat(jobDetail.isDurable).isTrue()
    }

    @Test
    fun `should create verification tokens cleanup trigger at 3 AM UTC`() {
        val jobDetail = config.cleanupExpiredVerificationTokensJobDetail()
        val trigger = config.cleanupExpiredVerificationTokensTrigger(jobDetail) as CronTrigger

        assertThat(trigger.cronExpression).isEqualTo("0 0 3 * * ?")
    }

    @Test
    fun `should create reset tokens cleanup job detail`() {
        val jobDetail: JobDetail = config.cleanupExpiredResetTokensJobDetail()

        assertThat(jobDetail.key.name).contains("reset")
        assertThat(jobDetail.isDurable).isTrue()
    }

    @Test
    fun `should create reset tokens cleanup trigger at 3 AM UTC`() {
        val jobDetail = config.cleanupExpiredResetTokensJobDetail()
        val trigger = config.cleanupExpiredResetTokensTrigger(jobDetail) as CronTrigger

        assertThat(trigger.cronExpression).isEqualTo("0 0 3 * * ?")
    }

    @Test
    fun `should create refresh tokens cleanup job detail`() {
        val jobDetail: JobDetail = config.cleanupExpiredRefreshTokensJobDetail()

        assertThat(jobDetail.key.name).contains("refresh")
        assertThat(jobDetail.isDurable).isTrue()
    }

    @Test
    fun `should create refresh tokens cleanup trigger at 4 AM UTC`() {
        val jobDetail = config.cleanupExpiredRefreshTokensJobDetail()
        val trigger = config.cleanupExpiredRefreshTokensTrigger(jobDetail) as CronTrigger

        assertThat(trigger.cronExpression).isEqualTo("0 0 4 * * ?")
    }

    @Test
    fun `should create outbox cleanup job detail`() {
        val jobDetail: JobDetail = config.cleanupPublishedOutboxJobDetail()

        assertThat(jobDetail.key.name).contains("outbox")
        assertThat(jobDetail.isDurable).isTrue()
    }

    @Test
    fun `should create outbox cleanup trigger at 5 AM UTC`() {
        val jobDetail = config.cleanupPublishedOutboxJobDetail()
        val trigger = config.cleanupPublishedOutboxTrigger(jobDetail) as CronTrigger

        assertThat(trigger.cronExpression).isEqualTo("0 0 5 * * ?")
    }
}
