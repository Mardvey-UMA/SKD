package com.skd.userinteractions

import com.skd.userinteractions.configuration.InteractionsProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(InteractionsProperties::class)
@EnableScheduling
class UserInteractionsServiceApplication

fun main(args: Array<String>) {
    runApplication<UserInteractionsServiceApplication>(*args)
}
