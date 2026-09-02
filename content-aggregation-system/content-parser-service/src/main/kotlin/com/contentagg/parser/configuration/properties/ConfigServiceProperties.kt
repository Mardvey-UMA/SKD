package com.contentagg.parser.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "config-service")
class ConfigServiceProperties {
    var url: String = ""
    var connectionTimeout: Long = 5000
    var readTimeout: Long = 10000
    var pollInterval: Long = 30000
    var cacheTtl: Long = 3600
}
