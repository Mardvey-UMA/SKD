package com.skd.userinteractions.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import org.postgresql.util.PGobject
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions

@Configuration
class JdbcConverterConfiguration(private val objectMapper: ObjectMapper) {

    @Bean
    fun jdbcCustomConversions(): JdbcCustomConversions = JdbcCustomConversions(
        listOf(
            MapToJsonbConverter(objectMapper),
            JsonbToMapConverter(objectMapper)
        )
    )

    @WritingConverter
    class MapToJsonbConverter(private val objectMapper: ObjectMapper) : Converter<Map<*, *>, PGobject> {
        override fun convert(source: Map<*, *>): PGobject = PGobject().apply {
            type = "jsonb"
            value = objectMapper.writeValueAsString(source)
        }
    }

    @ReadingConverter
    class JsonbToMapConverter(private val objectMapper: ObjectMapper) : Converter<PGobject, Map<String, Any?>> {
        @Suppress("UNCHECKED_CAST")
        override fun convert(source: PGobject): Map<String, Any?> =
            objectMapper.readValue(source.value ?: "{}", Map::class.java) as Map<String, Any?>
    }
}
