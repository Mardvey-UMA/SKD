package com.contentagg.parser.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.kafka")
class KafkaProperties {
    var bootstrapServers: String = ""
    var groupId: String = ""
    var topics: MutableMap<String, String> = mutableMapOf()
    var consumer: MutableMap<String, String> = mutableMapOf()
}
