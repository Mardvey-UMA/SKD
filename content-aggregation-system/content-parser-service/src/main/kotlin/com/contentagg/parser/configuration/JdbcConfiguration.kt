package com.contentagg.parser.configuration

import org.postgresql.util.PGobject
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration

/**
 * JDBC configuration for PostgreSQL JSONB → String reading conversion.
 * Required because Spring Data JDBC does not auto-convert PGobject (JSONB) to String.
 */
@Configuration
class JdbcConfiguration : AbstractJdbcConfiguration() {

    override fun jdbcCustomConversions(): JdbcCustomConversions {
        return JdbcCustomConversions(
            listOf(
                PGobjectToStringConverter(),
            )
        )
    }

    @ReadingConverter
    class PGobjectToStringConverter : Converter<PGobject, String> {
        override fun convert(source: PGobject): String {
            return source.value ?: ""
        }
    }
}
