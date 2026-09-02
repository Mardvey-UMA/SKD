package com.contentagg.parser.processor.telegram

import com.contentagg.parser.configuration.properties.TelegramProperties
import com.contentagg.parser.integration.telegram.TelegramAuthService
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
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

class TelegramContentParserTest {

    private lateinit var parser: TelegramContentParser
    private val telegramParseProcessor: TelegramParseProcessor = mockk()
    private val telegramAuthService: TelegramAuthService = mockk()
    private val telegramProperties: TelegramProperties = TelegramProperties().apply { enabled = true }

    @BeforeEach
    fun setUp() {
        parser = TelegramContentParser(
            telegramParseProcessor = telegramParseProcessor,
            telegramAuthService = telegramAuthService,
            telegramProperties = telegramProperties,
        )
    }

    @Nested
    @DisplayName("supports()")
    inner class SupportsTests {

        @Test
        @DisplayName("TELEGRAM source type — returns true")
        fun testSupports_TelegramSourceType_ReturnsTrue() {
            assertTrue(parser.supports("TELEGRAM"))
        }

        @Test
        @DisplayName("telegram lowercase — returns true (case-insensitive)")
        fun testSupports_TelegramLowercase_ReturnsTrue() {
            assertTrue(parser.supports("telegram"))
        }

        @Test
        @DisplayName("HABR source type — returns false")
        fun testSupports_OtherSourceType_ReturnsFalse() {
            assertFalse(parser.supports("HABR"))
        }

        @Test
        @DisplayName("VCRU source type — returns false")
        fun testSupports_Vcru_ReturnsFalse() {
            assertFalse(parser.supports("VCRU"))
        }
    }

    @Nested
    @DisplayName("parse()")
    inner class ParseTests {

        @Test
        @DisplayName("not authenticated — returns 0 without delegating to processor")
        fun testParse_NotAuthenticated_Returns0() {
            every { telegramAuthService.isAuthenticated() } returns false
            every { telegramAuthService.getAuthState() } returns TelegramAuthService.STATE_WAIT_CODE

            val result = parser.parse(buildSourceConfig())

            assertEquals(0, result)
            verify(exactly = 0) { telegramParseProcessor.parseChannel(any()) }
        }

        @Test
        @DisplayName("authenticated — delegates to TelegramParseProcessor and returns its result")
        fun testParse_Authenticated_DelegatesToProcessor() {
            every { telegramAuthService.isAuthenticated() } returns true
            every { telegramParseProcessor.parseChannel(any()) } returns 5

            val result = parser.parse(buildSourceConfig())

            assertEquals(5, result)
            verify(exactly = 1) { telegramParseProcessor.parseChannel(any()) }
        }

        @Test
        @DisplayName("telegram.enabled=false — returns 0 without calling processor")
        fun testParse_DisabledProperty_Returns0() {
            val disabledProperties = TelegramProperties().apply { enabled = false }
            val disabledParser = TelegramContentParser(
                telegramParseProcessor = telegramParseProcessor,
                telegramAuthService = telegramAuthService,
                telegramProperties = disabledProperties,
            )

            val result = disabledParser.parse(buildSourceConfig())

            assertEquals(0, result)
            verify(exactly = 0) { telegramParseProcessor.parseChannel(any()) }
            verify(exactly = 0) { telegramAuthService.isAuthenticated() }
        }

        @Test
        @DisplayName("authenticated with correct channelUsername — passes context with channel to processor")
        fun testParse_Authenticated_PassesCorrectContextToProcessor() {
            every { telegramAuthService.isAuthenticated() } returns true
            every { telegramParseProcessor.parseChannel(any()) } returns 3

            val sourceConfig = buildSourceConfig(channelUsername = "durov")
            parser.parse(sourceConfig)

            verify(exactly = 1) {
                telegramParseProcessor.parseChannel(
                    match { ctx -> ctx.channelUsername == "durov" }
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Builder helpers
    // -------------------------------------------------------------------------

    private fun buildSourceConfig(channelUsername: String = "testchannel"): SourceConfigResponse =
        SourceConfigResponse(
            id = "550e8400-e29b-41d4-a716-446655440001",
            sourceType = null,
            name = "telegram-test-source",
            url = "https://t.me/$channelUsername",
            updateFrequencyMinutes = 5,
            isActive = true,
            parameters = mapOf("channelUsername" to channelUsername),
            createdAt = null,
            updatedAt = null,
        )
}
