package com.skd.userinteractions.config

import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Overrides Quartz SchedulerFactoryBean properties in the test profile to use in-memory (RAM) job store.
 * The main application.yml sets driverDelegateClass (JDBC-only property), which causes
 * RAMJobStore context initialization to fail when job-store-type=memory is set in tests.
 * This customizer replaces the properties with a clean set for RAMJobStore.
 */
@Configuration
@Profile("test")
class TestQuartzConfig {

    @Bean
    fun testQuartzPropertiesCustomizer(): SchedulerFactoryBeanCustomizer =
        SchedulerFactoryBeanCustomizer { schedulerFactoryBean ->
            schedulerFactoryBean.setQuartzProperties(
                java.util.Properties().apply {
                    setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore")
                }
            )
        }
}
