package com.skd.subscription

import com.skd.subscription.configuration.ReconcileProperties
import com.skd.subscription.configuration.YookassaProperties
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(exclude = [ManagementWebSecurityAutoConfiguration::class])
@EnableScheduling
@EnableConfigurationProperties(YookassaProperties::class, ReconcileProperties::class)
class SubscriptionServiceApplication

fun main(args: Array<String>) {
    runApplication<SubscriptionServiceApplication>(*args)
}
