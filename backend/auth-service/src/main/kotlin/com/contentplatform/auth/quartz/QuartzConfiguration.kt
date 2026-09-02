package com.contentplatform.auth.quartz

import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Properties

@Configuration
class QuartzConfiguration {

    @Bean
    @ConditionalOnProperty(name = ["spring.quartz.job-store-type"], havingValue = "jdbc")
    fun postgresQuartzCustomizer(): SchedulerFactoryBeanCustomizer = SchedulerFactoryBeanCustomizer { factory ->
        val props = Properties()
        props["org.quartz.jobStore.driverDelegateClass"] = "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate"
        factory.setQuartzProperties(props)
    }

    @Bean
    fun cleanupExpiredVerificationTokensJobDetail(): JobDetail =
        JobBuilder.newJob(CleanupExpiredVerificationTokensJob::class.java)
            .withIdentity("cleanup-verification-tokens-job")
            .storeDurably()
            .build()

    @Bean
    fun cleanupExpiredVerificationTokensTrigger(
        cleanupExpiredVerificationTokensJobDetail: JobDetail
    ): Trigger =
        TriggerBuilder.newTrigger()
            .forJob(cleanupExpiredVerificationTokensJobDetail)
            .withIdentity("cleanup-verification-tokens-trigger")
            .withSchedule(CronScheduleBuilder.cronSchedule("0 0 3 * * ?"))
            .build()

    @Bean
    fun cleanupExpiredResetTokensJobDetail(): JobDetail =
        JobBuilder.newJob(CleanupExpiredResetTokensJob::class.java)
            .withIdentity("cleanup-reset-tokens-job")
            .storeDurably()
            .build()

    @Bean
    fun cleanupExpiredResetTokensTrigger(
        cleanupExpiredResetTokensJobDetail: JobDetail
    ): Trigger =
        TriggerBuilder.newTrigger()
            .forJob(cleanupExpiredResetTokensJobDetail)
            .withIdentity("cleanup-reset-tokens-trigger")
            .withSchedule(CronScheduleBuilder.cronSchedule("0 0 3 * * ?"))
            .build()

    @Bean
    fun cleanupExpiredRefreshTokensJobDetail(): JobDetail =
        JobBuilder.newJob(CleanupExpiredRefreshTokensJob::class.java)
            .withIdentity("cleanup-refresh-tokens-job")
            .storeDurably()
            .build()

    @Bean
    fun cleanupExpiredRefreshTokensTrigger(
        cleanupExpiredRefreshTokensJobDetail: JobDetail
    ): Trigger =
        TriggerBuilder.newTrigger()
            .forJob(cleanupExpiredRefreshTokensJobDetail)
            .withIdentity("cleanup-refresh-tokens-trigger")
            .withSchedule(CronScheduleBuilder.cronSchedule("0 0 4 * * ?"))
            .build()

    @Bean
    fun cleanupPublishedOutboxJobDetail(): JobDetail =
        JobBuilder.newJob(CleanupPublishedOutboxJob::class.java)
            .withIdentity("cleanup-outbox-job")
            .storeDurably()
            .build()

    @Bean
    fun cleanupPublishedOutboxTrigger(
        cleanupPublishedOutboxJobDetail: JobDetail
    ): Trigger =
        TriggerBuilder.newTrigger()
            .forJob(cleanupPublishedOutboxJobDetail)
            .withIdentity("cleanup-outbox-trigger")
            .withSchedule(CronScheduleBuilder.cronSchedule("0 0 5 * * ?"))
            .build()
}
