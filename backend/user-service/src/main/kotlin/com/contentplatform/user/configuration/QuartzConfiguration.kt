package com.contentplatform.user.configuration

import com.contentplatform.user.infrastructure.job.CleanupPublishedOutboxJob
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.TimeZone

@Configuration
class QuartzConfiguration {

    @Bean
    fun cleanupPublishedOutboxJobDetail(): JobDetail {
        return JobBuilder.newJob(CleanupPublishedOutboxJob::class.java)
            .withIdentity("cleanupPublishedOutboxJob", "outbox")
            .withDescription("Delete published outbox entries older than 3 days")
            .storeDurably()
            .build()
    }

    @Bean
    @Profile("!test")
    fun cleanupPublishedOutboxTrigger(cleanupPublishedOutboxJobDetail: JobDetail): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(cleanupPublishedOutboxJobDetail)
            .withIdentity("cleanupPublishedOutboxTrigger", "outbox")
            .withSchedule(
                CronScheduleBuilder.dailyAtHourAndMinute(3, 0)
                    .inTimeZone(TimeZone.getTimeZone("UTC"))
            )
            .build()
    }
}
