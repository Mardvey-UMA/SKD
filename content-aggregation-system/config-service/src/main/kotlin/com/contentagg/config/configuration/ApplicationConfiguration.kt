package com.contentagg.config.configuration

import com.contentagg.config.configuration.properties.CatalogProperties
import com.contentagg.config.configuration.properties.IntegrationProperties
import com.contentagg.config.configuration.properties.KafkaProperties
import com.contentagg.config.configuration.properties.SourceLimitProperties
import com.contentagg.config.configuration.properties.TelegramValidationProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing
import java.util.Optional

@Configuration(proxyBeanMethods = false)
@EnableJdbcAuditing(auditorAwareRef = "auditorProvider")
@EnableConfigurationProperties(
    KafkaProperties::class,
    CatalogProperties::class,
    IntegrationProperties::class,
    SourceLimitProperties::class,
    TelegramValidationProperties::class,
)
class ApplicationConfiguration {

    @Bean
    fun auditorProvider(): AuditorAware<String> = AuditorAware { Optional.of("config-service") }

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
