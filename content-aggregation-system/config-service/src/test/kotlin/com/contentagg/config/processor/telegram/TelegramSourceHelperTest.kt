package com.contentagg.config.processor.telegram

import com.contentagg.config.enums.SourceType
import com.contentagg.config.exception.InvalidSourceException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class TelegramSourceHelperTest {

    private lateinit var helper: TelegramSourceHelper

    @BeforeEach
    fun setUp() {
        helper = TelegramSourceHelper()
    }

    @Nested
    @DisplayName("validateTelegramSourceType()")
    inner class ValidateTelegramSourceTypeTests {

        @Test
        @DisplayName("TELEGRAM source type — passes validation without exception")
        fun testValidateTelegramSourceType_Telegram_NoException() {
            assertDoesNotThrow {
                helper.validateTelegramSourceType(SourceType.TELEGRAM)
            }
        }

        @Test
        @DisplayName("HABR source type — throws InvalidSourceException")
        fun testValidateTelegramSourceType_NonTelegram_ThrowsInvalidSourceException() {
            assertThrows<InvalidSourceException> {
                helper.validateTelegramSourceType(SourceType.HABR)
            }
        }

        @Test
        @DisplayName("VCRU source type — throws InvalidSourceException")
        fun testValidateTelegramSourceType_Vcru_ThrowsInvalidSourceException() {
            assertThrows<InvalidSourceException> {
                helper.validateTelegramSourceType(SourceType.VCRU)
            }
        }
    }

    @Nested
    @DisplayName("buildTelegramUrl()")
    inner class BuildTelegramUrlTests {

        @Test
        @DisplayName("channel username with '@' prefix — strips '@' and returns correct URL")
        fun testBuildTelegramUrl_WithAtPrefix_StripsAt() {
            val result = helper.buildTelegramUrl("@durov")
            assertEquals("https://t.me/durov", result)
        }

        @Test
        @DisplayName("channel username without '@' prefix — returns correct URL")
        fun testBuildTelegramUrl_WithoutAtPrefix_ReturnsCorrectUrl() {
            val result = helper.buildTelegramUrl("durov")
            assertEquals("https://t.me/durov", result)
        }

        @Test
        @DisplayName("channel username — URL uses https://t.me/ base")
        fun testBuildTelegramUrl_CorrectBaseUrl() {
            val result = helper.buildTelegramUrl("testchannel")
            assertTrue(result.startsWith("https://t.me/"), "URL should start with https://t.me/")
            assertTrue(result.contains("testchannel"), "URL should contain channel username")
        }
    }

    @Nested
    @DisplayName("buildParametersMap()")
    inner class BuildParametersMapTests {

        @Test
        @DisplayName("all parameters provided — map contains all keys with correct values")
        fun testBuildParametersMap_AllParams_ContainsAllKeys() {
            val result = helper.buildParametersMap(
                channelUsername = "durov",
                downloadMedia = true,
                maxMessages = 200,
                maxMediaSizeMb = 100,
                batchSize = 25,
            )

            assertEquals("durov", result["channelUsername"])
            assertEquals("true", result["downloadMedia"])
            assertEquals("200", result["maxMessages"])
            assertEquals("100", result["maxMediaSizeMb"])
            assertEquals("25", result["batchSize"])
        }

        @Test
        @DisplayName("null optional parameters — uses default values")
        fun testBuildParametersMap_NullOptionals_UsesDefaults() {
            val result = helper.buildParametersMap(
                channelUsername = "testchannel",
                downloadMedia = null,
                maxMessages = null,
                maxMediaSizeMb = null,
                batchSize = null,
            )

            assertEquals("true", result["downloadMedia"])
            assertEquals("100", result["maxMessages"])
            assertEquals("50", result["maxMediaSizeMb"])
            assertEquals("50", result["batchSize"])
        }

        @Test
        @DisplayName("channelUsername with '@' prefix — '@' is stripped in parameters map")
        fun testBuildParametersMap_ChannelUsernameWithAt_StrippedInMap() {
            val result = helper.buildParametersMap(
                channelUsername = "@durov",
                downloadMedia = false,
                maxMessages = 50,
                maxMediaSizeMb = 30,
                batchSize = 10,
            )

            assertEquals("durov", result["channelUsername"])
            assertFalse(result["channelUsername"]!!.startsWith("@"), "channelUsername must not contain '@' prefix")
        }

        @Test
        @DisplayName("all values in map are serialized as strings")
        fun testBuildParametersMap_AllValuesAreStrings() {
            val result = helper.buildParametersMap(
                channelUsername = "channel",
                downloadMedia = false,
                maxMessages = 100,
                maxMediaSizeMb = 50,
                batchSize = 50,
            )

            result.values.forEach { value ->
                assertTrue(value.isNotEmpty(), "All parameter values should be non-empty strings, got: '$value'")
            }
        }
    }
}
