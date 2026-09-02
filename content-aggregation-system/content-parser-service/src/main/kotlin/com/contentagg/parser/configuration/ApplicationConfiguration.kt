package com.contentagg.parser.configuration

import com.contentagg.parser.configuration.properties.CacheProperties
import com.contentagg.parser.configuration.properties.ConfigServiceProperties
import com.contentagg.parser.configuration.properties.KafkaProperties
import com.contentagg.parser.configuration.properties.S3Properties
import com.contentagg.parser.configuration.properties.TelegramProperties
import com.contentagg.parser.configuration.properties.scheduler.SchedulerProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(
    KafkaProperties::class,
    S3Properties::class,
    ConfigServiceProperties::class,
    SchedulerProperties::class,
    CacheProperties::class,
    TelegramProperties::class,
)
class ApplicationConfiguration {

    @Bean
    fun jacksonCustomizer(): Jackson2ObjectMapperBuilderCustomizer =
        Jackson2ObjectMapperBuilderCustomizer { builder ->
            builder
                .modules(KotlinModule.Builder().build(), JavaTimeModule())
                .featuresToDisable(
                    SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                )
                .serializationInclusion(JsonInclude.Include.NON_EMPTY)
        }
}
