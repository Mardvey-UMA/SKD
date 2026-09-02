package com.contentagg.parser.configuration.properties.scheduler

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "parser")
class SchedulerProperties {
    var cronExpression: String = "0 */5 * * * ?"
    var publishCronExpression: String = "0/30 * * * * ?"
    var publishBatchSize: Int = 100

    // Horizontal-scaling properties (feature: parser-horizontal-scaling)
    var enabledSourceTypes: List<String> = listOf("HABR", "VCRU", "TELEGRAM")
    var executeIntervalSeconds: Int = 5
    var reclaimCronExpression: String = "0 */1 * * * ?"
    var staleTaskThresholdMinutes: Int = 10
    var maxRetryCount: Int = 3
}
