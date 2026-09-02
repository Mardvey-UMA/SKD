package com.contentagg.parser.integration

import io.mockk.mockk
import org.quartz.Scheduler
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Test configuration providing a MockK-based Scheduler bean.
 *
 * Required because integration tests exclude QuartzAutoConfiguration
 * (to prevent Quartz from connecting to the real JDBC store), but
 * TaskReclaimer depends on Scheduler via constructor injection.
 *
 * This configuration is explicitly imported via @Import in each
 * integration test class (or the AbstractIntegrationTest base).
 */
@TestConfiguration
class TestSchedulerConfiguration {

    @Bean
    @Primary
    fun scheduler(): Scheduler = mockk(relaxed = true)
}
