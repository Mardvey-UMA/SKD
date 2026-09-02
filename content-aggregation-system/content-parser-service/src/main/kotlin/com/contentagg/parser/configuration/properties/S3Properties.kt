package com.contentagg.parser.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "s3")
class S3Properties {
    var endpoint: String = ""
    var accessKey: String = ""
    var secretKey: String = ""
    var region: String = "us-east-1"
    var bucket: String = ""
    var publicUrl: String = "http://localhost:9002"
}
