package com.contentagg.parser.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "telegram")
class TelegramProperties {
    var apiId: Int = 0
    var apiHash: String = ""
    var phoneNumber: String = ""
    var sessionPath: String = "/app/telegram-session"
    var downloadPath: String = "/app/telegram-downloads"
    var requestDelayMs: Long = 1000
    var maxMediaSizeMb: Int = 50
    var enabled: Boolean = false
    var adminToken: String = ""
}
