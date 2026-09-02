package com.contentagg.config.configuration

import org.postgresql.util.PGobject
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration

@Configuration
class JdbcConfiguration : AbstractJdbcConfiguration() {

    override fun userConverters(): List<*> = listOf(
        PGobjectToStringConverter(),
        StringToPGobjectConverter()
    )

    @ReadingConverter
    class PGobjectToStringConverter : Converter<PGobject, String> {
        override fun convert(source: PGobject): String = source.value ?: "{}"
    }

    @WritingConverter
    class StringToPGobjectConverter : Converter<String, PGobject> {
        override fun convert(source: String): PGobject = PGobject().apply {
            type = "jsonb"
            value = source
        }
    }
}
