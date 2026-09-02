package com.contentagg.parser.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "cache")
class CacheProperties {
    var sourceConfigs: CacheItemProperties = CacheItemProperties()
    var sourceConfig: CacheItemProperties = CacheItemProperties()

    class CacheItemProperties {
        var expireAfterWrite: Duration = Duration.ofMinutes(30)
        var maximumSize: Long = 1000
    }
}
