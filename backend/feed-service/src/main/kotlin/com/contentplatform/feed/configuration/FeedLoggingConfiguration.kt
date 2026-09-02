package com.contentplatform.feed.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class FeedLoggingConfiguration {

    @Bean(name = ["feedLoggingExecutor"])
    fun feedLoggingExecutor(): ThreadPoolTaskExecutor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 500
            setThreadNamePrefix("feed-log-")
            initialize()
        }
    }
}
