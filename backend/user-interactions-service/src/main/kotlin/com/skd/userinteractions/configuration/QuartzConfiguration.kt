package com.skd.userinteractions.configuration

import com.skd.userinteractions.job.CreateNextPartitionJob
import com.skd.userinteractions.job.DropOldPartitionsJob
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.TriggerBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class QuartzConfiguration {

    @Bean
    fun createPartitionJobDetail(): JobDetail {
        return JobBuilder.newJob(CreateNextPartitionJob::class.java)
            .withIdentity("createPartition")
            .storeDurably()
            .build()
    }

    @Bean
    fun dropPartitionJobDetail(): JobDetail {
        return JobBuilder.newJob(DropOldPartitionsJob::class.java)
            .withIdentity("dropPartition")
            .storeDurably()
            .build()
    }

    @Bean
    fun createPartitionTrigger(createPartitionJobDetail: JobDetail) =
        TriggerBuilder.newTrigger()
            .forJob(createPartitionJobDetail)
            .withIdentity("createPartitionTrigger")
            .withSchedule(CronScheduleBuilder.cronSchedule("0 0 0 1 * ?"))
            .build()

    @Bean
    fun dropPartitionTrigger(dropPartitionJobDetail: JobDetail) =
        TriggerBuilder.newTrigger()
            .forJob(dropPartitionJobDetail)
            .withIdentity("dropPartitionTrigger")
            .withSchedule(CronScheduleBuilder.cronSchedule("0 0 1 1 * ?"))
            .build()
}
