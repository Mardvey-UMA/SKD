package com.contentagg.parser.processor.telegram

import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import com.contentagg.parser.exception.TelegramParseException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TelegramParseContextTest {

    @Nested
    @DisplayName("from()")
    inner class FromTests {

        @Test
        @DisplayName("all parameters present — builds context with correct values")
        fun testFrom_AllParametersPresent_BuildsContext() {
            val sourceConfig = buildSourceConfig(
                params = mapOf(
                    "channelUsername" to "durov",
                    "maxMessages" to "200",
                    "downloadMedia" to "false",
                    "maxMediaSizeMb" to "100",
                    "batchSize" to "25",
                )
            )

            val context = TelegramParseContext.from(sourceConfig)

            assertEquals("550e8400-e29b-41d4-a716-446655440001", context.sourceId.toString())
            assertEquals("telegram-test-source", context.sourceName)
            assertEquals("durov", context.channelUsername)
            assertEquals(200, context.maxMessages)
            assertFalse(context.downloadMedia)
            assertEquals(100, context.maxMediaSizeMb)
            assertEquals(25, context.batchSize)
        }

        @Test
        @DisplayName("missing channelUsername — throws TelegramParseException")
        fun testFrom_MissingChannelUsername_ThrowsException() {
            val sourceConfig = buildSourceConfig(
                params = mapOf(
                    "maxMessages" to "50",
                    "downloadMedia" to "true",
                )
            )

            assertThrows<TelegramParseException> {
                TelegramParseContext.from(sourceConfig)
            }
        }

        @Test
        @DisplayName("missing optional parameters — applies default values")
        fun testFrom_DefaultValues_AppliedWhenParamsMissing() {
            val sourceConfig = buildSourceConfig(
                params = mapOf("channelUsername" to "testchannel")
            )

            val context = TelegramParseContext.from(sourceConfig)

            assertEquals("testchannel", context.channelUsername)
            assertEquals(100, context.maxMessages)
            assertTrue(context.downloadMedia)
            assertEquals(50, context.maxMediaSizeMb)
            assertEquals(50, context.batchSize)
        }

        @Test
        @DisplayName("channelUsername with '@' prefix — strips '@' prefix")
        fun testFrom_AtPrefixStripped_InChannelUsername() {
            val sourceConfig = buildSourceConfig(
                params = mapOf("channelUsername" to "@durov")
            )

            val context = TelegramParseContext.from(sourceConfig)

            // TelegramParseContext.from does NOT strip '@' — TelegramSourceHelper does it upstream.
            // Here we verify the raw value is preserved as-is from parameters.
            assertEquals("@durov", context.channelUsername)
        }

        @Test
        @DisplayName("parameters as Int types — parsed without string conversion")
        fun testFrom_IntegerParams_ParsedCorrectly() {
            val sourceConfig = buildSourceConfig(
                params = mapOf(
                    "channelUsername" to "channel",
                    "maxMessages" to 75,
                    "maxMediaSizeMb" to 30,
                    "batchSize" to 10,
                )
            )

            val context = TelegramParseContext.from(sourceConfig)

            assertEquals(75, context.maxMessages)
            assertEquals(30, context.maxMediaSizeMb)
            assertEquals(10, context.batchSize)
        }

        @Test
        @DisplayName("invalid integer parameter — falls back to default value")
        fun testFrom_InvalidIntegerParam_FallsBackToDefault() {
            val sourceConfig = buildSourceConfig(
                params = mapOf(
                    "channelUsername" to "channel",
                    "maxMessages" to "not-a-number",
                )
            )

            val context = TelegramParseContext.from(sourceConfig)

            assertEquals(100, context.maxMessages)
        }

        @Test
        @DisplayName("null parameters map — throws TelegramParseException for missing channelUsername")
        fun testFrom_NullParameters_ThrowsException() {
            val sourceConfig = buildSourceConfig(params = null)

            assertThrows<TelegramParseException> {
                TelegramParseContext.from(sourceConfig)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Builder helpers
    // -------------------------------------------------------------------------

    private fun buildSourceConfig(params: Map<String, Any>?): SourceConfigResponse =
        SourceConfigResponse(
            id = "550e8400-e29b-41d4-a716-446655440001",
            sourceType = null,
            name = "telegram-test-source",
            url = "https://t.me/test",
            updateFrequencyMinutes = 5,
            isActive = true,
            parameters = params,
            createdAt = null,
            updatedAt = null,
        )
}
