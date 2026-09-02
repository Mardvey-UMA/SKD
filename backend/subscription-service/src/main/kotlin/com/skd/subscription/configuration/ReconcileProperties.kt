package com.skd.subscription.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "subscription.reconcile")
data class ReconcileProperties(
    var lazy: Lazy = Lazy(),
    var scheduled: Scheduled = Scheduled()
) {
    data class Lazy(
        var enabled: Boolean = true,
        var pendingWindowMinutes: Long = 15
    )

    data class Scheduled(
        var enabled: Boolean = true,
        var cron: String = "0 * * * * ?",
        var pendingWindowMinutes: Long = 30
    )
}
