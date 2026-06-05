package com.specthinker.config

import com.specthinker.spec.Sections
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import org.springframework.data.jdbc.core.dialect.JdbcDialect
import org.springframework.data.jdbc.core.dialect.JdbcH2Dialect
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations
import java.time.Instant

@Configuration
class PersistenceConfig : AbstractJdbcConfiguration() {

    @Bean
    override fun jdbcDialect(operations: NamedParameterJdbcOperations): JdbcDialect = JdbcH2Dialect.INSTANCE

    @Bean
    override fun jdbcCustomConversions(): JdbcCustomConversions =
        JdbcCustomConversions(
            listOf(
                SectionsToStringConverter(),
                StringToSectionsConverter(),
                InstantToStringConverter(),
                LenientStringToInstantConverter(),
            ),
        )
}

@WritingConverter
private class SectionsToStringConverter : Converter<Sections, String> {
    override fun convert(source: Sections): String =
        Sections.JSON.encodeToString(Sections.serializer(), source)
}

@ReadingConverter
private class StringToSectionsConverter : Converter<String, Sections> {
    override fun convert(source: String): Sections =
        Sections.JSON.decodeFromString(Sections.serializer(), source)
}

@WritingConverter
private class InstantToStringConverter : Converter<Instant, String> {
    override fun convert(source: Instant): String = source.toString()
}

@ReadingConverter
private class LenientStringToInstantConverter : Converter<String, Instant> {
    override fun convert(source: String): Instant {
        if (source.isBlank()) return Instant.EPOCH
        val asLong = source.toLongOrNull()
        return if (asLong != null) Instant.ofEpochMilli(asLong) else Instant.parse(source)
    }
}
