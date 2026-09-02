package com.contentagg.aggregator.configuration

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.postgresql.util.PGobject
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Configuration
@EnableJdbcAuditing
class ApplicationConfiguration : AbstractJdbcConfiguration() {

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

    override fun jdbcCustomConversions(): JdbcCustomConversions =
        JdbcCustomConversions(listOf(
            PGobjectToStringConverter(),
            TimestampToOffsetDateTimeConverter(),
            OffsetDateTimeToTimestampConverter(),
        ))

    @ReadingConverter
    class PGobjectToStringConverter : Converter<PGobject, String> {
        override fun convert(source: PGobject): String? = source.value
    }

    @ReadingConverter
    class TimestampToOffsetDateTimeConverter : Converter<Timestamp, OffsetDateTime> {
        override fun convert(source: Timestamp): OffsetDateTime =
            source.toInstant().atOffset(ZoneOffset.UTC)
    }

    @WritingConverter
    class OffsetDateTimeToTimestampConverter : Converter<OffsetDateTime, Timestamp> {
        override fun convert(source: OffsetDateTime): Timestamp =
            Timestamp.from(source.toInstant())
    }
}
