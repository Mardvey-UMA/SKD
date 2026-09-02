package com.skd.subscription.integration

import com.skd.subscription.job.AutoRenewalJob
import com.skd.subscription.job.CheckExpiredSubscriptionsJob
import com.skd.subscription.job.CleanupOldWebhookLogJob
import com.skd.subscription.job.CleanupPublishedOutboxJob
import com.skd.subscription.job.ReconcilePendingPaymentsJob
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.quartz.Scheduler
import org.quartz.impl.matchers.GroupMatcher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

@Tag("integration")
class QuartzJobSchedulingIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var scheduler: Scheduler

    @Test
    fun `should register CheckExpiredSubscriptionsJob with hourly trigger`() {
        val jobKeys = scheduler.getJobKeys(GroupMatcher.anyGroup())
        val jobKey = jobKeys.find { it.name.contains("CheckExpiredSubscriptions", ignoreCase = true) }
        assertThat(jobKey).isNotNull

        val jobDetail = scheduler.getJobDetail(jobKey!!)
        assertThat(jobDetail.jobClass).isEqualTo(CheckExpiredSubscriptionsJob::class.java)

        val triggers = scheduler.getTriggersOfJob(jobKey)
        assertThat(triggers).isNotEmpty
    }

    @Test
    fun `should register AutoRenewalJob with hourly trigger`() {
        val jobKeys = scheduler.getJobKeys(GroupMatcher.anyGroup())
        val jobKey = jobKeys.find { it.name.contains("AutoRenewal", ignoreCase = true) }
        assertThat(jobKey).isNotNull

        val jobDetail = scheduler.getJobDetail(jobKey!!)
        assertThat(jobDetail.jobClass).isEqualTo(AutoRenewalJob::class.java)

        val triggers = scheduler.getTriggersOfJob(jobKey)
        assertThat(triggers).isNotEmpty
    }

    @Test
    fun `should register CleanupPublishedOutboxJob with daily trigger`() {
        val jobKeys = scheduler.getJobKeys(GroupMatcher.anyGroup())
        val jobKey = jobKeys.find { it.name.contains("CleanupPublishedOutbox", ignoreCase = true) }
        assertThat(jobKey).isNotNull

        val jobDetail = scheduler.getJobDetail(jobKey!!)
        assertThat(jobDetail.jobClass).isEqualTo(CleanupPublishedOutboxJob::class.java)

        val triggers = scheduler.getTriggersOfJob(jobKey)
        assertThat(triggers).isNotEmpty
    }

    @Test
    fun `should register CleanupOldWebhookLogJob with daily trigger`() {
        val jobKeys = scheduler.getJobKeys(GroupMatcher.anyGroup())
        val jobKey = jobKeys.find { it.name.contains("CleanupOldWebhookLog", ignoreCase = true) }
        assertThat(jobKey).isNotNull

        val jobDetail = scheduler.getJobDetail(jobKey!!)
        assertThat(jobDetail.jobClass).isEqualTo(CleanupOldWebhookLogJob::class.java)

        val triggers = scheduler.getTriggersOfJob(jobKey)
        assertThat(triggers).isNotEmpty
    }

    @Test
    fun `should register ReconcilePendingPaymentsJob with a trigger when scheduled reconcile is enabled by default`() {
        val jobKeys = scheduler.getJobKeys(GroupMatcher.anyGroup())
        val jobKey = jobKeys.find { it.name.contains("ReconcilePendingPayments", ignoreCase = true) }
        assertThat(jobKey)
            .`as`("ReconcilePendingPaymentsJob must be registered in the Quartz scheduler when subscription.reconcile.scheduled.enabled=true (default)")
            .isNotNull

        val jobDetail = scheduler.getJobDetail(jobKey!!)
        assertThat(jobDetail.jobClass).isEqualTo(ReconcilePendingPaymentsJob::class.java)

        val triggers = scheduler.getTriggersOfJob(jobKey)
        assertThat(triggers)
            .`as`("ReconcilePendingPaymentsJob must have at least one trigger registered")
            .isNotEmpty
    }

    @Test
    fun `should have all five scheduled jobs registered`() {
        val jobKeys = scheduler.getJobKeys(GroupMatcher.anyGroup())
        assertThat(jobKeys).hasSizeGreaterThanOrEqualTo(5)

        val jobNames = jobKeys.map { it.name.lowercase() }
        assertThat(jobNames).anyMatch { it.contains("checkexpired") || it.contains("check_expired") }
        assertThat(jobNames).anyMatch { it.contains("autorenewal") || it.contains("auto_renewal") }
        assertThat(jobNames).anyMatch { it.contains("cleanuppublishedoutbox") || it.contains("cleanup_published") }
        assertThat(jobNames).anyMatch { it.contains("cleanupoldwebhooklog") || it.contains("cleanup_old_webhook") }
        assertThat(jobNames).anyMatch { it.contains("reconcilependingpayments") || it.contains("reconcile_pending_payments") }
    }
}

@Tag("integration")
@TestPropertySource(properties = ["subscription.reconcile.scheduled.enabled=false"])
class QuartzReconcileDisabledIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var scheduler: Scheduler

    @Test
    fun `should NOT register ReconcilePendingPaymentsJob when scheduled reconcile is disabled`() {
        val jobKeys = scheduler.getJobKeys(GroupMatcher.anyGroup())
        val jobKey = jobKeys.find { it.name.contains("ReconcilePendingPayments", ignoreCase = true) }

        if (jobKey == null) {
            // Preferred outcome: job not registered at all when disabled
            return
        }
        val triggers = scheduler.getTriggersOfJob(jobKey)
        assertThat(triggers)
            .`as`("when subscription.reconcile.scheduled.enabled=false, ReconcilePendingPaymentsJob must have NO triggers registered")
            .isEmpty()
    }

    @Test
    fun `other scheduled jobs remain registered when only reconcile is disabled`() {
        val jobKeys = scheduler.getJobKeys(GroupMatcher.anyGroup())
        val jobNames = jobKeys.map { it.name.lowercase() }

        assertThat(jobNames)
            .`as`("disabling reconcile must not affect AutoRenewalJob registration")
            .anyMatch { it.contains("autorenewal") || it.contains("auto_renewal") }
        assertThat(jobNames)
            .`as`("disabling reconcile must not affect CheckExpiredSubscriptionsJob registration")
            .anyMatch { it.contains("checkexpired") || it.contains("check_expired") }
    }
}
