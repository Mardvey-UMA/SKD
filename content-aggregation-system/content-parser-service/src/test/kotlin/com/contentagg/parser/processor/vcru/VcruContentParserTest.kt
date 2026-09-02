package com.contentagg.parser.processor.vcru

import com.contentagg.parser.db.service.util.VcruSourceSubtype
import com.contentagg.parser.exception.VcruParseException
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import com.contentagg.parser.integration.rest.vcru.VcruApiClient
import com.contentagg.parser.integration.rest.vcru.model.VcruSubsiteDto
import com.contentagg.parser.processor.vcru.strategy.VcruParseStrategy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VcruContentParserTest {

    private lateinit var parser: VcruContentParser
    private val userStrategy: VcruParseStrategy = mockk()
    private val companyStrategy: VcruParseStrategy = mockk()
    private val vcruApiClient: VcruApiClient = mockk()

    @BeforeEach
    fun setUp() {
        every { userStrategy.getSubtype() } returns VcruSourceSubtype.USER
        every { companyStrategy.getSubtype() } returns VcruSourceSubtype.COMPANY

        parser = VcruContentParser(
            strategies = listOf(userStrategy, companyStrategy),
            vcruApiClient = vcruApiClient,
        )
    }

    @Nested
    @DisplayName("supports()")
    inner class SupportsTests {

        @Test
        @DisplayName("supports VCRU source type — returns true")
        fun supports_Vcru_ReturnsTrue() {
            val result = parser.supports("VCRU")
            assertTrue(result)
        }

        @Test
        @DisplayName("does not support HABR source type — returns false")
        fun supports_Habr_ReturnsFalse() {
            val result = parser.supports("HABR")
            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("parse()")
    inner class ParseTests {

        @Test
        @DisplayName("subsite.type==1 delegates to USER strategy")
        fun parse_SubsiteType1_DelegatesToUserStrategy() {
            val sourceConfig = buildSourceConfig("artem")
            every { vcruApiClient.getSubsiteInfo("artem") } returns buildSubsiteDto(type = 1)
            every { userStrategy.parse(sourceConfig, any()) } returns 3

            val result = parser.parse(sourceConfig)

            assertEquals(3, result)
            verify(exactly = 1) { userStrategy.parse(sourceConfig, any()) }
            verify(exactly = 0) { companyStrategy.parse(any(), any()) }
        }

        @Test
        @DisplayName("subsite.type==2 delegates to COMPANY strategy")
        fun parse_SubsiteType2_DelegatesToCompanyStrategy() {
            val sourceConfig = buildSourceConfig("tinkoff")
            every { vcruApiClient.getSubsiteInfo("tinkoff") } returns buildSubsiteDto(type = 2)
            every { companyStrategy.parse(sourceConfig, any()) } returns 5

            val result = parser.parse(sourceConfig)

            assertEquals(5, result)
            verify(exactly = 1) { companyStrategy.parse(sourceConfig, any()) }
            verify(exactly = 0) { userStrategy.parse(any(), any()) }
        }

        @Test
        @DisplayName("missing vcruAlias in parameters throws VcruParseException")
        fun parse_MissingAlias_ThrowsVcruParseException() {
            val sourceConfig = buildSourceConfig(alias = null)

            assertThrows<VcruParseException> {
                parser.parse(sourceConfig)
            }

            verify(exactly = 0) { vcruApiClient.getSubsiteInfo(any()) }
        }

        @Test
        @DisplayName("API returning null subsite (404) returns 0 (skip)")
        fun parse_SubsiteApiReturnsNull_Returns0() {
            val sourceConfig = buildSourceConfig("artem")
            every { vcruApiClient.getSubsiteInfo("artem") } returns null

            val result = parser.parse(sourceConfig)

            assertEquals(0, result)
            verify(exactly = 0) { userStrategy.parse(any(), any()) }
            verify(exactly = 0) { companyStrategy.parse(any(), any()) }
        }

        @Test
        @DisplayName("unknown subsite type (99) defaults to USER strategy")
        fun parse_UnknownSubsiteType_DefaultsToUserStrategy() {
            val sourceConfig = buildSourceConfig("artem")
            every { vcruApiClient.getSubsiteInfo("artem") } returns buildSubsiteDto(type = 99)
            every { userStrategy.parse(sourceConfig, any()) } returns 1

            val result = parser.parse(sourceConfig)

            assertEquals(1, result)
            verify(exactly = 1) { userStrategy.parse(sourceConfig, any()) }
        }
    }

    // -------------------------------------------------------------------------
    // Builder helpers
    // -------------------------------------------------------------------------

    private fun buildSourceConfig(alias: String?): SourceConfigResponse {
        val parameters: Map<String, Any>? = if (alias != null) {
            mapOf(
                "vcruAlias" to alias,
                "parseImages" to "false",
                "maxArticles" to "20",
                "sorting" to "new",
            )
        } else {
            emptyMap()
        }
        return SourceConfigResponse(
            id = "550e8400-e29b-41d4-a716-446655440001",
            sourceType = null,
            name = "vcru-test-source",
            url = if (alias != null) "https://vc.ru/$alias" else "https://vc.ru",
            updateFrequencyMinutes = 10,
            isActive = true,
            parameters = parameters,
            createdAt = null,
            updatedAt = null,
        )
    }

    private fun buildSubsiteDto(type: Int): VcruSubsiteDto = VcruSubsiteDto(
        id = 12345L,
        uri = "test-alias",
        url = "https://vc.ru/test-alias",
        type = type,
        subtype = null,
        name = "Test Subsite",
        nickname = "test-alias",
        description = null,
        counters = null,
    )
}
