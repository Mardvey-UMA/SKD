package com.skd.userinteractions.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "interactions")
class InteractionsProperties {
    var batch: Batch = Batch()
    var validation: Validation = Validation()
    var partition: Partition = Partition()

    class Batch {
        var flushIntervalMs: Long = 30000
        var maxEventsPerUser: Int = 50
    }

    class Validation {
        var maxEventsPerBatch: Int = 100
        var maxTimestampFutureSec: Int = 60
        var maxTimestampAgeHours: Int = 24
    }

    class Partition {
        var retentionMonths: Int = 6
    }
}
