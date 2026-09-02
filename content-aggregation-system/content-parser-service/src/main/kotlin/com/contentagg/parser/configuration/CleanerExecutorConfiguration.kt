package com.contentagg.parser.configuration

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

@Configuration
class CleanerExecutorConfiguration {

    @Bean(name = ["cleanerTaskExecutor"])
    fun cleanerTaskExecutor(
        @Value("\${parser.cleaner.thread-pool-size:2}") poolSize: Int,
    ): AsyncTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = poolSize
        executor.maxPoolSize = poolSize
        executor.queueCapacity = 4
        executor.setThreadNamePrefix("content-cleaner-")
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.DiscardOldestPolicy())
        executor.initialize()
        return executor
    }
}
