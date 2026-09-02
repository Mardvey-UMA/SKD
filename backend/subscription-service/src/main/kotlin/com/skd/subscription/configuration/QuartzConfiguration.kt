package com.skd.subscription.configuration

import com.skd.subscription.db.repository.outbox.OutboxRepository
import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.repository.paymentmethod.SavedPaymentMethodRepository
import com.skd.subscription.db.repository.plan.PlanRepository
import com.skd.subscription.db.repository.subscription.SubscriptionRepository
import com.skd.subscription.db.repository.webhook.WebhookLogRepository
import com.skd.subscription.db.service.PaymentReconciler
import com.skd.subscription.integration.rest.yookassa.YookassaClient
import com.skd.subscription.job.AutoRenewalJob
import com.skd.subscription.job.CheckExpiredSubscriptionsJob
import com.skd.subscription.job.CleanupOldWebhookLogJob
import com.skd.subscription.job.CleanupPublishedOutboxJob
import com.skd.subscription.job.ReconcilePendingPaymentsJob
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.quartz.spi.TriggerFiredBundle
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.quartz.AdaptableJobFactory

@Configuration
class QuartzConfiguration(
    private val subscriptionRepository: SubscriptionRepository,
    private val outboxRepository: OutboxRepository,
    private val webhookLogRepository: WebhookLogRepository,
    private val savedPaymentMethodRepository: SavedPaymentMethodRepository,
    private val paymentRepository: PaymentRepository,
    private val planRepository: PlanRepository,
    private val yookassaClient: YookassaClient,
    private val paymentReconciler: PaymentReconciler,
    private val reconcileProperties: ReconcileProperties
) {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = ["spring.quartz.job-store-type"],
        havingValue = "jdbc"
    )
    fun quartzJdbcCustomizer(): org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer {
        return org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer { schedulerFactoryBean ->
            schedulerFactoryBean.setQuartzProperties(
                java.util.Properties().apply {
                    setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate")
                }
            )
        }
    }

    @Bean
    fun quartzSchedulerFactoryBeanCustomizer(): org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer {
        return org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer { schedulerFactoryBean ->
            schedulerFactoryBean.setJobFactory(springBeanJobFactory())
        }
    }

    @Bean
    fun springBeanJobFactory(): DelegatingJobFactory {
        return DelegatingJobFactory(
            subscriptionRepository = subscriptionRepository,
            outboxRepository = outboxRepository,
            webhookLogRepository = webhookLogRepository,
            savedPaymentMethodRepository = savedPaymentMethodRepository,
            paymentRepository = paymentRepository,
            planRepository = planRepository,
            yookassaClient = yookassaClient,
            paymentReconciler = paymentReconciler,
            reconcileProperties = reconcileProperties
        )
    }

    // ---- CheckExpiredSubscriptionsJob ----

    @Bean
    fun checkExpiredSubscriptionsJobDetail(): JobDetail =
        JobBuilder.newJob(CheckExpiredSubscriptionsJob::class.java)
            .withIdentity("CheckExpiredSubscriptionsJob")
            .storeDurably()
            .build()

    @Bean
    fun checkExpiredSubscriptionsTrigger(checkExpiredSubscriptionsJobDetail: JobDetail): Trigger =
        TriggerBuilder.newTrigger()
            .forJob(checkExpiredSubscriptionsJobDetail)
            .withIdentity("CheckExpiredSubscriptionsTrigger")
            .withSchedule(CronScheduleBuilder.cronSchedule("0 0 * * * ?"))
            .build()

    // ---- AutoRenewalJob ----

    @Bean
    fun autoRenewalJobDetail(): JobDetail =
        JobBuilder.newJob(AutoRenewalJob::class.java)
            .withIdentity("AutoRenewalJob")
            .storeDurably()
            .build()

    @Bean
    fun autoRenewalTrigger(autoRenewalJobDetail: JobDetail): Trigger =
        TriggerBuilder.newTrigger()
            .forJob(autoRenewalJobDetail)
            .withIdentity("AutoRenewalTrigger")
            .withSchedule(CronScheduleBuilder.cronSchedule("0 0 * * * ?"))
            .build()

    // ---- CleanupPublishedOutboxJob ----

    @Bean
    fun cleanupPublishedOutboxJobDetail(): JobDetail =
        JobBuilder.newJob(CleanupPublishedOutboxJob::class.java)
            .withIdentity("CleanupPublishedOutboxJob")
            .storeDurably()
            .build()

    @Bean
    fun cleanupPublishedOutboxTrigger(cleanupPublishedOutboxJobDetail: JobDetail): Trigger =
        TriggerBuilder.newTrigger()
            .forJob(cleanupPublishedOutboxJobDetail)
            .withIdentity("CleanupPublishedOutboxTrigger")
            .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(3, 0))
            .build()

    // ---- CleanupOldWebhookLogJob ----

    @Bean
    fun cleanupOldWebhookLogJobDetail(): JobDetail =
        JobBuilder.newJob(CleanupOldWebhookLogJob::class.java)
            .withIdentity("CleanupOldWebhookLogJob")
            .storeDurably()
            .build()

    @Bean
    fun cleanupOldWebhookLogTrigger(cleanupOldWebhookLogJobDetail: JobDetail): Trigger =
        TriggerBuilder.newTrigger()
            .forJob(cleanupOldWebhookLogJobDetail)
            .withIdentity("CleanupOldWebhookLogTrigger")
            .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(4, 0))
            .build()

    // ---- ReconcilePendingPaymentsJob ----

    @Bean
    @ConditionalOnProperty(name = ["subscription.reconcile.scheduled.enabled"], havingValue = "true", matchIfMissing = true)
    fun reconcilePendingPaymentsJobDetail(): JobDetail =
        JobBuilder.newJob(ReconcilePendingPaymentsJob::class.java)
            .withIdentity("ReconcilePendingPaymentsJob")
            .storeDurably()
            .build()

    @Bean
    @ConditionalOnProperty(name = ["subscription.reconcile.scheduled.enabled"], havingValue = "true", matchIfMissing = true)
    fun reconcilePendingPaymentsTrigger(reconcilePendingPaymentsJobDetail: JobDetail): Trigger =
        TriggerBuilder.newTrigger()
            .forJob(reconcilePendingPaymentsJobDetail)
            .withIdentity("ReconcilePendingPaymentsTrigger")
            .withSchedule(CronScheduleBuilder.cronSchedule(reconcileProperties.scheduled.cron))
            .build()

    /**
     * Custom job factory that uses constructor injection by delegating to
     * known Spring-managed dependencies for each job type.
     */
    class DelegatingJobFactory(
        private val subscriptionRepository: SubscriptionRepository,
        private val outboxRepository: OutboxRepository,
        private val webhookLogRepository: WebhookLogRepository,
        private val savedPaymentMethodRepository: SavedPaymentMethodRepository,
        private val paymentRepository: PaymentRepository,
        private val planRepository: PlanRepository,
        private val yookassaClient: YookassaClient,
        private val paymentReconciler: PaymentReconciler,
        private val reconcileProperties: ReconcileProperties
    ) : AdaptableJobFactory() {

        override fun createJobInstance(bundle: TriggerFiredBundle): Any {
            return when (bundle.jobDetail.jobClass) {
                CheckExpiredSubscriptionsJob::class.java ->
                    CheckExpiredSubscriptionsJob(
                        subscriptionRepository = subscriptionRepository,
                        outboxRepository = outboxRepository
                    )
                AutoRenewalJob::class.java ->
                    AutoRenewalJob(
                        subscriptionRepository = subscriptionRepository,
                        savedPaymentMethodRepository = savedPaymentMethodRepository,
                        paymentRepository = paymentRepository,
                        planRepository = planRepository,
                        yookassaClient = yookassaClient
                    )
                CleanupPublishedOutboxJob::class.java ->
                    CleanupPublishedOutboxJob(
                        outboxRepository = outboxRepository
                    )
                CleanupOldWebhookLogJob::class.java ->
                    CleanupOldWebhookLogJob(
                        webhookLogRepository = webhookLogRepository
                    )
                ReconcilePendingPaymentsJob::class.java ->
                    ReconcilePendingPaymentsJob(
                        paymentRepository = paymentRepository,
                        paymentReconciler = paymentReconciler,
                        reconcileProperties = reconcileProperties
                    )
                else -> super.createJobInstance(bundle)
            }
        }
    }
}
